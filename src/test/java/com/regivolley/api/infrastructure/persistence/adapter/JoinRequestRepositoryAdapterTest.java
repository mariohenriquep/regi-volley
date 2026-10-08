package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class JoinRequestRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JoinRequestRepository requests;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private EntityManager entityManager;

    private Association newAssociation() {
        return associations.save(Fixtures.association());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private JoinRequest saveAndReload(JoinRequest request) {
        JoinRequest saved = requests.save(request);
        flushAndClear();
        return requests.findById(saved.associationId(), saved.id()).orElseThrow();
    }

    @Test
    void aPendingRequestComesBackWithContactAndConsent() {
        // Arrange
        Association association = newAssociation();
        JoinRequest request = Fixtures.joinRequest(association.id(), "Rita", NOW);

        // Act
        JoinRequest loaded = saveAndReload(request);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(request);
        assertThat(loaded.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(loaded.consent().policyVersion()).isEqualTo("2026-01");
    }

    @Test
    void approvedAndRejectedDecisionsKeepWhoWhenAndWhy() {
        // Arrange
        Association association = newAssociation();
        MemberId admin = MemberId.generate();
        Instant decidedAt = NOW.plusSeconds(60);
        JoinRequest approved = Fixtures.joinRequest(association.id(), "Rita", NOW)
                .approve(admin, Fixtures.at(decidedAt));
        JoinRequest rejected = Fixtures.joinRequest(association.id(), "Rui", NOW)
                .reject(admin, "  Not a local  ", Fixtures.at(decidedAt));

        // Act
        JoinRequest loadedApproved = saveAndReload(approved);
        JoinRequest loadedRejected = saveAndReload(rejected);

        // Assert
        assertThat(loadedApproved).usingRecursiveComparison().isEqualTo(approved);
        assertThat(loadedRejected).usingRecursiveComparison().isEqualTo(rejected);
        assertThat(loadedApproved.decidedBy()).contains(admin);
        assertThat(loadedRejected.rejectionReason()).contains("Not a local");
    }

    @Test
    void aPendingRequestAnonymisedOnErasureIsStoredAsWithdrawn() {
        // Arrange
        Association association = newAssociation();
        JoinRequest erased = Fixtures.joinRequest(association.id(), "Rita", NOW).anonymise(Fixtures.at(NOW.plusSeconds(5)));

        // Act
        JoinRequest loaded = saveAndReload(erased);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(erased);
        assertThat(loaded.status()).isEqualTo(JoinRequestStatus.REJECTED);
        assertThat(loaded.decidedBy()).isEmpty();
        assertThat(loaded.phone()).isEmpty();
    }

    @Test
    void aDecisionOnAStoredRequestUpdatesItInPlace() {
        // Arrange
        Association association = newAssociation();
        MemberId admin = MemberId.generate();
        JoinRequest pending = requests.save(Fixtures.joinRequest(association.id(), "Rita", NOW));
        flushAndClear();

        // Act
        JoinRequest loaded = saveAndReload(pending.reject(admin, "Full", Fixtures.at(NOW.plusSeconds(30))));

        // Assert
        assertThat(loaded.status()).isEqualTo(JoinRequestStatus.REJECTED);
        assertThat(loaded.decidedBy()).contains(admin);
        assertThat(requests.findPending(association.id())).isEmpty();
    }

    @Test
    void findPendingReturnsOnlyPendingRequestsOldestFirst() {
        // Arrange
        Association association = newAssociation();
        JoinRequest newer = requests.save(Fixtures.joinRequest(association.id(), "Newer", NOW.plusSeconds(100)));
        JoinRequest older = requests.save(Fixtures.joinRequest(association.id(), "Older", NOW));
        requests.save(Fixtures.joinRequest(association.id(), "Decided", NOW)
                .reject(MemberId.generate(), null, Fixtures.at(NOW.plusSeconds(1))));
        flushAndClear();

        // Act
        List<JoinRequest> pending = requests.findPending(association.id());

        // Assert
        assertThat(pending).extracting(JoinRequest::id).containsExactly(older.id(), newer.id());
    }

    @Test
    void requestsAreInvisibleToAnotherAssociationByIdAndInThePendingList() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        JoinRequest ofA = requests.save(Fixtures.joinRequest(a.id(), "Rita", NOW));
        JoinRequest ofB = requests.save(Fixtures.joinRequest(b.id(), "Rui", NOW));
        flushAndClear();

        // Act
        boolean visibleToB = requests.findById(b.id(), ofA.id()).isPresent();

        // Assert
        assertThat(visibleToB).isFalse();
        assertThat(requests.findPending(a.id())).extracting(JoinRequest::id).containsExactly(ofA.id());
        assertThat(requests.findPending(b.id())).extracting(JoinRequest::id).containsExactly(ofB.id());
    }

    @Test
    void aSecondDecisionMadeOnAStaleCopyIsRejectedAndTheFirstStays() {
        // Arrange
        Association association = newAssociation();
        MemberId admin = MemberId.generate();
        JoinRequest stored = requests.save(Fixtures.joinRequest(association.id(), "Rita", NOW));
        flushAndClear();
        JoinRequest copyA = requests.findById(association.id(), stored.id()).orElseThrow();
        JoinRequest copyB = requests.findById(association.id(), stored.id()).orElseThrow();
        requests.save(copyB.approve(admin, Fixtures.at(NOW.plusSeconds(30))));
        flushAndClear();
        Executable act = () -> requests.save(copyA.reject(admin, "Full", Fixtures.at(NOW.plusSeconds(31))));

        // Act
        JoinRequestModifiedConcurrentlyException ex = assertThrows(JoinRequestModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.joinRequestId()).isEqualTo(stored.id());
        assertThat(requests.findById(association.id(), stored.id()).orElseThrow().status())
                .isEqualTo(JoinRequestStatus.APPROVED);
    }

    @Test
    void aNewRequestStartsAtVersionZeroAndEachSaveMovesItByOne() {
        // Arrange
        Association association = newAssociation();
        JoinRequest stored = requests.save(Fixtures.joinRequest(association.id(), "Rita", NOW));
        flushAndClear();

        // Act
        JoinRequest decided = requests.save(requests.findById(association.id(), stored.id()).orElseThrow()
                .reject(MemberId.generate(), null, Fixtures.at(NOW.plusSeconds(30))));

        // Assert
        assertThat(stored.version()).isZero();
        assertThat(decided.version()).isEqualTo(1L);
    }

    private JoinRequest requestFrom(Association association, String email) {
        return JoinRequestFactory.create(association.id(), ContactDetails.of("Rita", EmailAddress.of(email), PhoneNumber.of("912345678")),
                true, "2026-01", Fixtures.at(NOW));
    }

    @Test
    void findPendingByEmailFindsOnlyAPendingRequestOfThatAssociation() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        JoinRequest pending = requests.save(requestFrom(a, "rita@example.com"));
        JoinRequest decided = requests.save(requestFrom(a, "done@example.com"));
        requests.save(decided.reject(MemberId.generate(), null, Fixtures.at(NOW.plusSeconds(5))));
        requests.save(requestFrom(b, "other@example.com"));
        flushAndClear();

        // Act
        var found = requests.findPendingByEmail(a.id(), EmailAddress.of("rita@example.com"));
        var decidedFound = requests.findPendingByEmail(a.id(), EmailAddress.of("done@example.com"));
        var asOtherAssociation = requests.findPendingByEmail(b.id(), EmailAddress.of("rita@example.com"));

        // Assert
        assertThat(found).hasValueSatisfying(request -> assertThat(request.id()).isEqualTo(pending.id()));
        assertThat(decidedFound).isEmpty();
        assertThat(asOtherAssociation).isEmpty();
    }

    @Test
    void twoPendingRequestsWithTheSameEmailInOneAssociationAreRejectedWithoutRevealingIt() {
        // Arrange
        Association association = newAssociation();
        requests.save(requestFrom(association, "rita@example.com"));
        flushAndClear();
        Executable act = () -> requests.save(requestFrom(association, "rita@example.com"));

        // Act
        JoinRequestNotPossibleException ex = assertThrows(JoinRequestNotPossibleException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain("rita");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void theSameEmailMayRequestAgainOnceTheEarlierRequestWasDecidedOrInAnotherAssociation() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        JoinRequest first = requests.save(requestFrom(a, "rita@example.com"));
        requests.save(first.reject(MemberId.generate(), null, Fixtures.at(NOW.plusSeconds(5))));
        flushAndClear();

        // Act
        JoinRequest again = requests.save(requestFrom(a, "rita@example.com"));
        JoinRequest elsewhere = requests.save(requestFrom(b, "rita@example.com"));

        // Assert
        assertThat(again.isPending()).isTrue();
        assertThat(elsewhere.isPending()).isTrue();
    }
}
