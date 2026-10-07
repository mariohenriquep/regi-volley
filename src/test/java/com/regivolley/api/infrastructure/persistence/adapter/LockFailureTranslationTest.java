package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.AssociationModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.PlanModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.TrainingGroupModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.CannotAcquireLockException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Taking the root row lock first (architecture.md section 10) means a writer can time out or be chosen
 * as a deadlock victim while waiting for it. Every adapter must report that as the aggregate's own
 * conflict, never as a raw Spring or Hibernate exception. A mocked Spring Data repository stands in for
 * the lock that cannot be obtained.
 */
class LockFailureTranslationTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final Association association = Fixtures.association();

    @Test
    void sessionSaveReportsALockItCouldNotTake() {
        // Arrange
        SessionJpaRepository repository = mock(SessionJpaRepository.class);
        when(repository.findForUpdateByIdAndAssociationId(any(), any())).thenThrow(new CannotAcquireLockException("busy"));
        var session = Fixtures.session(association.id(), Fixtures.SESSION_START, 12);
        Executable act = () -> new SessionRepositoryAdapter(repository, entityManager).save(session);

        // Act
        SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.sessionId()).isEqualTo(session.id());
        assertThat(ex.getCause()).isInstanceOf(CannotAcquireLockException.class);
    }

    @Test
    void trainingGroupSaveReportsALockItCouldNotTake() {
        // Arrange
        TrainingGroupJpaRepository repository = mock(TrainingGroupJpaRepository.class);
        when(repository.findForUpdateByIdAndAssociationId(any(), any())).thenThrow(new CannotAcquireLockException("busy"));
        var group = Fixtures.group(association.id(), "Open play", Set.of(association.entryLevelId()), MemberId.generate());
        Executable act = () -> new TrainingGroupRepositoryAdapter(repository, entityManager).save(group);

        // Act
        TrainingGroupModifiedConcurrentlyException ex = assertThrows(TrainingGroupModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.trainingGroupId()).isEqualTo(group.id());
    }

    @Test
    void associationSaveReportsALockItCouldNotTake() {
        // Arrange
        AssociationJpaRepository repository = mock(AssociationJpaRepository.class);
        when(repository.findForUpdateById(any())).thenThrow(new LockTimeoutException("timeout"));
        Executable act = () -> new AssociationRepositoryAdapter(repository, entityManager).save(association);

        // Act
        AssociationModifiedConcurrentlyException ex = assertThrows(AssociationModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.associationId()).isEqualTo(association.id());
    }

    @Test
    void subscriptionSaveReportsALockItCouldNotTake() {
        // Arrange
        SubscriptionJpaRepository repository = mock(SubscriptionJpaRepository.class);
        when(repository.findForUpdateByIdAndAssociationId(any(), any())).thenThrow(new CannotAcquireLockException("busy"));
        var subscription = Fixtures.subscription(Fixtures.pack(association.id(), Set.of()), MemberId.generate(), "2026-10-01");
        Executable act = () -> new SubscriptionRepositoryAdapter(repository, entityManager).save(subscription);

        // Act
        SubscriptionModifiedConcurrentlyException ex = assertThrows(SubscriptionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.subscriptionId()).isEqualTo(subscription.id());
    }

    @Test
    void memberSaveReportsALockItCouldNotTake() {
        // Arrange
        MemberJpaRepository repository = mock(MemberJpaRepository.class);
        when(repository.findForUpdateByIdAndAssociationId(any(), any())).thenThrow(new CannotAcquireLockException("busy"));
        Member member = Fixtures.member(association, "Ana");
        Executable act = () -> new MemberRepositoryAdapter(repository, entityManager).save(member);

        // Act
        MemberModifiedConcurrentlyException ex = assertThrows(MemberModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.memberId()).isEqualTo(member.id());
    }

    @Test
    void joinRequestSaveReportsALockItCouldNotTake() {
        // Arrange
        JoinRequestJpaRepository repository = mock(JoinRequestJpaRepository.class);
        when(repository.findForUpdateByIdAndAssociationId(any(), any())).thenThrow(new CannotAcquireLockException("busy"));
        var request = Fixtures.joinRequest(association.id(), "Rita", Fixtures.NOW);
        Executable act = () -> new JoinRequestRepositoryAdapter(repository, entityManager).save(request);

        // Act
        JoinRequestModifiedConcurrentlyException ex = assertThrows(JoinRequestModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.joinRequestId()).isEqualTo(request.id());
    }

    @Test
    void planSaveReportsALockItCouldNotTake() {
        // Arrange
        PlanJpaRepository repository = mock(PlanJpaRepository.class);
        when(repository.findForUpdateByIdAndAssociationId(any(), any())).thenThrow(new LockTimeoutException("timeout"));
        var plan = Fixtures.pack(association.id(), Set.of());
        Executable act = () -> new PlanRepositoryAdapter(repository, entityManager).save(plan);

        // Act
        PlanModifiedConcurrentlyException ex = assertThrows(PlanModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.planId()).isEqualTo(plan.id());
    }
}
