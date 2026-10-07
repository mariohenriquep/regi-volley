package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.function.Function;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Personal data must not be silently overwritten by a stale copy (architecture.md section 10, RGPD):
 * real, separate, committed transactions race on the same member or join request.
 */
@SpringBootTest
class PersonalDataConcurrencyTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AssociationRepository associations;
    @Autowired
    private MemberRepository members;
    @Autowired
    private JoinRequestRepository joinRequests;

    @Test
    void anErasureRacingAStaleEditStoresExactlyOneAndTheErasureIsNeverUndone() throws Exception {
        for (int round = 0; round < 15; round++) {
            // Arrange
            Association association = associations.save(Fixtures.association());
            Member stored = members.save(Fixtures.member(association, "Ana Silva"));
            Function<Member, Member> erase = member -> members.save(member.anonymise(Fixtures.at(Fixtures.NOW.plusSeconds(5))));
            Function<Member, Member> edit = member -> members.save(member.grantRole(MemberRole.COACH));

            // Act
            List<Throwable> failures = Races.race(
                    () -> members.findById(association.id(), stored.id()).orElseThrow(), List.of(erase, edit));

            // Assert
            assertThat(failures).as("round %d", round).hasSize(1)
                    .allMatch(MemberModifiedConcurrentlyException.class::isInstance);
            Member afterRace = members.findById(association.id(), stored.id()).orElseThrow();
            if (afterRace.isAnonymised()) {
                assertThat(afterRace.name()).isEqualTo(ContactDetails.ANONYMISED_NAME);
                assertThat(afterRace.phone()).isEmpty();
                assertThat(afterRace.email()).isNotEqualTo(stored.email());
            } else {
                Member retried = members.save(afterRace.anonymise(Fixtures.at(Fixtures.NOW.plusSeconds(6))));
                assertThat(members.findById(association.id(), retried.id()).orElseThrow().isAnonymised()).isTrue();
            }
        }
    }

    @Test
    void twoLevelChangesAtOnceStoreExactlyOneInsteadOfCollidingOnTheHistory() throws Exception {
        for (int round = 0; round < 15; round++) {
            // Arrange
            Association association = associations.save(Fixtures.association());
            Member stored = members.save(Fixtures.member(association, "Ana"));
            MemberId coachA = MemberId.generate();
            MemberId coachB = MemberId.generate();
            Function<Member, Member> toIntermediate = member -> members.save(member.changeLevel(
                    association, association.levels().get(1).id(), coachA, Fixtures.at(Fixtures.NOW.plusSeconds(10))));
            Function<Member, Member> toAdvanced = member -> members.save(member.changeLevel(
                    association, association.levels().get(2).id(), coachB, Fixtures.at(Fixtures.NOW.plusSeconds(10))));

            // Act
            List<Throwable> failures = Races.race(
                    () -> members.findById(association.id(), stored.id()).orElseThrow(),
                    List.of(toIntermediate, toAdvanced));

            // Assert
            assertThat(failures).as("round %d", round).hasSize(1)
                    .allMatch(MemberModifiedConcurrentlyException.class::isInstance);
            assertThat(members.findById(association.id(), stored.id()).orElseThrow().levelChanges()).hasSize(1);
        }
    }

    @Test
    void anApprovalRacingARejectionStoresExactlyOneDecision() throws Exception {
        for (int round = 0; round < 15; round++) {
            // Arrange
            Association association = associations.save(Fixtures.association());
            JoinRequest stored = joinRequests.save(Fixtures.joinRequest(association.id(), "Rita", Fixtures.NOW));
            MemberId admin = MemberId.generate();
            Function<JoinRequest, JoinRequest> approve = request -> joinRequests.save(
                    request.approve(association, admin, CLOCK).request());
            Function<JoinRequest, JoinRequest> reject = request -> joinRequests.save(
                    request.reject(admin, "Full", CLOCK));

            // Act
            List<Throwable> failures = Races.race(
                    () -> joinRequests.findById(association.id(), stored.id()).orElseThrow(), List.of(approve, reject));

            // Assert
            assertThat(failures).as("round %d", round).hasSize(1)
                    .allMatch(JoinRequestModifiedConcurrentlyException.class::isInstance);
            JoinRequest decided = joinRequests.findById(association.id(), stored.id()).orElseThrow();
            assertThat(decided.status()).isIn(JoinRequestStatus.APPROVED, JoinRequestStatus.REJECTED);
            assertThat(decided.version()).isEqualTo(1L);
        }
    }
}
