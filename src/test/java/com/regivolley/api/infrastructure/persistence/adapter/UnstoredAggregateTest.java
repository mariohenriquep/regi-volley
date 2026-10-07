package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.AssociationModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.PlanModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.TrainingGroupModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.SESSION_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An aggregate that carries a version was stored once; if there is no such row in its association
 * (gone, or never visible here) saving it must not silently insert a copy nor surface a raw primary
 * key error: it is the same conflict as any other lost race.
 */
@PersistenceTest
class UnstoredAggregateTest extends AbstractPostgresIntegrationTest {

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
    @Autowired
    private JdbcTemplate jdbc;

    private Association association() {
        return associations.save(Fixtures.association());
    }

    @Test
    void aSessionWithAVersionButNoStoredRowIsAConflictNotAnInsert() {
        // Arrange
        Association a = association();
        Session session = Fixtures.session(a.id(), SESSION_START, 12);
        Session loadedElsewhere = Session.reconstruct(session.id(), a.id(), session.trainingGroupId(), session.coachId(),
                session.startsAt(), session.endsAt(), 12, SessionStatus.SCHEDULED, null, List.of(), 3L);
        Executable act = () -> sessions.save(loadedElsewhere);

        // Act
        SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.sessionId()).isEqualTo(session.id());
        assertThat(jdbc.queryForObject("select count(*) from sessions where id = ?", Integer.class, session.id().value())).isZero();
    }

    @Test
    void aSessionOfAnotherAssociationWithAVersionIsAConflictToo() {
        // Arrange
        Association a = association();
        Association b = association();
        Session stored = sessions.save(Fixtures.session(a.id(), SESSION_START, 12));
        Session asB = Session.reconstruct(stored.id(), b.id(), stored.trainingGroupId(), stored.coachId(),
                stored.startsAt(), stored.endsAt(), 1, SessionStatus.SCHEDULED, null, List.of(), 1L);
        Executable act = () -> sessions.save(asB);

        // Act
        assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(sessions.findById(a.id(), stored.id()).orElseThrow().capacity()).isEqualTo(12);
    }

    @Test
    void aTrainingGroupWithAVersionButNoStoredRowIsAConflict() {
        // Arrange
        Association a = association();
        TrainingGroup group = Fixtures.group(a.id(), "Open play", Set.of(a.entryLevelId()), MemberId.generate());
        TrainingGroup loaded = TrainingGroup.reconstruct(group.id(), a.id(), "Open play", group.acceptedLevels(),
                group.venueId(), group.schedule(), 12, group.coachId(), TrainingGroupStatus.ACTIVE, 2L);
        Executable act = () -> groups.save(loaded);

        // Act
        TrainingGroupModifiedConcurrentlyException ex = assertThrows(TrainingGroupModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.trainingGroupId()).isEqualTo(group.id());
    }

    @Test
    void anAssociationWithAVersionButNoStoredRowIsAConflict() {
        // Arrange
        Association never = Fixtures.association();
        Association loaded = Association.reconstruct(never.id(), never.name(), never.shortName(), null, never.locality(),
                never.contactEmail(), never.bookingPolicy(), never.sessionGenerationPolicy(), never.levels(),
                never.entryLevelId(), 4L);
        Executable act = () -> associations.save(loaded);

        // Act
        AssociationModifiedConcurrentlyException ex = assertThrows(AssociationModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.associationId()).isEqualTo(never.id());
    }

    @Test
    void aSubscriptionWithAVersionButNoStoredRowIsAConflict() {
        // Arrange
        Association a = association();
        Subscription fresh = Fixtures.subscription(Fixtures.pack(a.id(), Set.of()), MemberId.generate(), "2026-10-01");
        Subscription loaded = Subscription.reconstruct(fresh.id(), a.id(), fresh.memberId(), fresh.planId(), fresh.terms(),
                fresh.startDate(), fresh.endDate(), PaymentStatus.PENDING, List.of(), 5L);
        Executable act = () -> subscriptions.save(loaded);

        // Act
        SubscriptionModifiedConcurrentlyException ex = assertThrows(SubscriptionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.subscriptionId()).isEqualTo(fresh.id());
    }

    @Test
    void aMemberWithAVersionButNoStoredRowIsAConflict() {
        // Arrange
        Association a = association();
        Member fresh = Fixtures.member(a, "Ana");
        Member loaded = Member.reconstruct(fresh.id(), a.id(), fresh.contact(), fresh.consent(), MemberStatus.ACTIVE,
                fresh.levelId(), fresh.roles(), List.of(), fresh.joinedAt(), null, 1L);
        Executable act = () -> members.save(loaded);

        // Act
        MemberModifiedConcurrentlyException ex = assertThrows(MemberModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.memberId()).isEqualTo(fresh.id());
    }

    @Test
    void aJoinRequestWithAVersionButNoStoredRowIsAConflict() {
        // Arrange
        Association a = association();
        JoinRequest fresh = Fixtures.joinRequest(a.id(), "Rita", Fixtures.NOW);
        JoinRequest loaded = JoinRequest.reconstruct(fresh.id(), a.id(), fresh.contact(), fresh.consent(),
                JoinRequestStatus.PENDING, fresh.requestedAt(), null, null, null, null, 1L);
        Executable act = () -> joinRequests.save(loaded);

        // Act
        JoinRequestModifiedConcurrentlyException ex = assertThrows(JoinRequestModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.joinRequestId()).isEqualTo(fresh.id());
    }

    @Test
    void aPlanWithAVersionButNoStoredRowIsAConflict() {
        // Arrange
        Association a = association();
        Plan fresh = Fixtures.pack(a.id(), Set.of());
        Plan loaded = Plan.reconstruct(fresh.id(), a.id(), fresh.name(), fresh.terms(), fresh.price(), 90, 2L);
        Executable act = () -> plans.save(loaded);

        // Act
        PlanModifiedConcurrentlyException ex = assertThrows(PlanModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.planId()).isEqualTo(fresh.id());
    }
}
