package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.SESSION_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Association B must never be able to overwrite a row of association A, even knowing its id: the
 * save looks the row up inside B, finds nothing, tries to insert, and the primary key stops it.
 * Needs committed data (the attempt runs in its own transaction), hence a full context.
 */
@SpringBootTest
class CrossTenantOverwriteTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AssociationRepository associations;
    @Autowired
    private SessionRepository sessions;
    @Autowired
    private MemberRepository members;
    @Autowired
    private PlanRepository plans;
    @Autowired
    private TrainingGroupRepository groups;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private JoinRequestRepository joinRequests;

    private Association a;
    private Association b;

    private void twoAssociations() {
        a = associations.save(Fixtures.association());
        b = associations.save(Fixtures.association());
    }

    @Test
    void aSessionOfAnotherAssociationIsNotOverwritten() {
        // Arrange
        twoAssociations();
        Session ofA = sessions.save(Fixtures.session(a.id(), SESSION_START, 12));
        Session hijack = Session.reconstruct(ofA.id(), b.id(), ofA.trainingGroupId(), ofA.coachId(), ofA.startsAt(),
                ofA.endsAt(), 1, SessionStatus.SCHEDULED, null, List.of(), 0L);
        Executable act = () -> sessions.save(hijack);

        // Act
        assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(sessions.findById(a.id(), ofA.id()).orElseThrow().capacity()).isEqualTo(12);
        assertThat(sessions.findById(b.id(), ofA.id())).isEmpty();
    }

    @Test
    void aMemberOfAnotherAssociationIsNotOverwritten() {
        // Arrange
        twoAssociations();
        Member ofA = members.save(Fixtures.member(a, "Ana"));
        Member hijack = Member.reconstruct(ofA.id(), b.id(), Fixtures.contact("Mallory"), ofA.consent(),
                MemberStatus.ACTIVE, b.entryLevelId(), Set.of(MemberRole.ADMIN),
                List.of(), ofA.joinedAt(), null, 0L);
        Executable act = () -> members.save(hijack);

        // Act
        assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(members.findById(a.id(), ofA.id()).orElseThrow().name()).isEqualTo("Ana");
        assertThat(members.findById(b.id(), ofA.id())).isEmpty();
    }

    @Test
    void aPlanOfAnotherAssociationIsNotOverwritten() {
        // Arrange
        twoAssociations();
        Plan ofA = plans.save(Fixtures.pack(a.id(), Set.of()));
        Plan hijack = Plan.reconstruct(ofA.id(), b.id(), "Hijacked", ofA.terms(), ofA.price(), 90, 0L);
        Executable act = () -> plans.save(hijack);

        // Act
        assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(plans.findById(a.id(), ofA.id()).orElseThrow().name()).isEqualTo("Pack of 10");
        assertThat(plans.findById(b.id(), ofA.id())).isEmpty();
    }

    @Test
    void aTrainingGroupOfAnotherAssociationIsNotOverwritten() {
        // Arrange
        twoAssociations();
        TrainingGroup ofA = groups.save(Fixtures.group(a.id(), "Open play", Set.of(a.entryLevelId()), MemberId.generate()));
        TrainingGroup hijack = TrainingGroup.reconstruct(ofA.id(), b.id(), "Hijacked", Set.of(b.entryLevelId()),
                ofA.venueId(), ofA.schedule(), 1, ofA.coachId(), TrainingGroupStatus.ACTIVE, 0L);
        Executable act = () -> groups.save(hijack);

        // Act
        assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(groups.findById(a.id(), ofA.id()).orElseThrow().name()).isEqualTo("Open play");
        assertThat(groups.findById(b.id(), ofA.id())).isEmpty();
    }

    @Test
    void aSubscriptionOfAnotherAssociationIsNotOverwritten() {
        // Arrange
        twoAssociations();
        Subscription ofA = subscriptions.save(
                Fixtures.subscription(Fixtures.pack(a.id(), Set.of()), MemberId.generate(), "2026-10-01"));
        Subscription hijack = Subscription.reconstruct(ofA.id(), b.id(), ofA.memberId(), ofA.planId(), ofA.terms(),
                ofA.startDate(), ofA.endDate(), PaymentStatus.PAID, List.of(), 0L);
        Executable act = () -> subscriptions.save(hijack);

        // Act
        assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(subscriptions.findById(a.id(), ofA.id()).orElseThrow().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(subscriptions.findById(b.id(), ofA.id())).isEmpty();
    }

    @Test
    void aJoinRequestOfAnotherAssociationIsNotOverwritten() {
        // Arrange
        twoAssociations();
        JoinRequest ofA = joinRequests.save(Fixtures.joinRequest(a.id(), "Rita", Fixtures.NOW));
        JoinRequest hijack = JoinRequest.reconstruct(ofA.id(), b.id(), Fixtures.contact("Mallory"), ofA.consent(),
                JoinRequestStatus.PENDING, ofA.requestedAt(), null, null, null, null, 0L);
        Executable act = () -> joinRequests.save(hijack);

        // Act
        assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(joinRequests.findById(a.id(), ofA.id()).orElseThrow().name()).isEqualTo("Rita");
        assertThat(joinRequests.findById(b.id(), ofA.id())).isEmpty();
    }

    @Test
    void anAssociationCannotTakeAnotherAssociationsLevelRow() {
        // Arrange
        twoAssociations();
        Level stolen = Level.reconstruct(a.entryLevelId(), b.id(), "Stolen", 0);
        Association hijack = Association.reconstruct(b.id(), b.name(), b.shortName(), null, b.locality(),
                b.contactEmail(), b.bookingPolicy(), b.sessionGenerationPolicy(), NoShowPolicy.defaults(), List.of(stolen), stolen.id(),
                b.version());
        Executable act = () -> associations.save(hijack);

        // Act
        assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(associations.findById(a.id()).orElseThrow().entryLevel().name()).isEqualTo("Beginner");
        assertThat(associations.findById(b.id()).orElseThrow().levels()).hasSize(3);
    }
}
