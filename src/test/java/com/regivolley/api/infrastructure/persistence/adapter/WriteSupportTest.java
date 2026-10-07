package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.PlanModifiedConcurrentlyException;
import com.regivolley.api.domain.model.valueobject.PlanId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.LockModeType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;

import java.sql.SQLException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WriteSupportTest {

    private static final String INDEX = "uq_bookings_live_member_session";

    @Test
    void forcesTheIncrementWhenTheVersionDidNotMove() {
        // Arrange
        EntityManager entityManager = mock(EntityManager.class);
        Object entity = new Object();

        // Act
        WriteSupport.ensureVersionIncremented(entityManager, entity, 3L, 3L);

        // Assert
        verify(entityManager).lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void doesNotIncrementTwiceWhenHibernateAlreadyMovedTheVersion() {
        // Arrange
        EntityManager entityManager = mock(EntityManager.class);
        Object entity = new Object();

        // Act
        WriteSupport.ensureVersionIncremented(entityManager, entity, 3L, 4L);

        // Assert
        verify(entityManager, never()).lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void recognisesAViolationByItsConstraintNameCaseInsensitively() {
        // Arrange
        Throwable failure = new RuntimeException("wrapper",
                new ConstraintViolationException("boom", new SQLException("x"), INDEX.toUpperCase()));

        // Act
        boolean violates = WriteSupport.violates(failure, INDEX);

        // Assert
        assertThat(violates).isTrue();
    }

    @Test
    void recognisesAViolationByTheQuotedNameInAMessageDeepInTheCauseChain() {
        // Arrange
        Throwable failure = new RuntimeException("outer",
                new RuntimeException("middle", new SQLException("duplicate key value violates unique constraint \"" + INDEX + "\"")));

        // Act
        boolean violates = WriteSupport.violates(failure, INDEX);

        // Assert
        assertThat(violates).isTrue();
    }

    @Test
    void doesNotMatchAnotherConstraint() {
        // Arrange
        Throwable failure = new RuntimeException("wrapper",
                new ConstraintViolationException("boom", new SQLException("duplicate key \"uq_other\""), "uq_other"));

        // Act
        boolean violates = WriteSupport.violates(failure, INDEX);

        // Assert
        assertThat(violates).isFalse();
    }

    @Test
    void survivesFailuresWithoutMessagesOrWithASelfReferencingCause() {
        // Arrange
        Throwable silent = new RuntimeException((String) null);
        Throwable selfCaused = new RuntimeException("loop") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        // Act
        boolean silentViolates = WriteSupport.violates(silent, INDEX);
        boolean loopViolates = WriteSupport.violates(selfCaused, INDEX);

        // Assert
        assertThat(silentViolates).isFalse();
        assertThat(loopViolates).isFalse();
    }

    private static Stream<RuntimeException> lockFailures() {
        return Stream.of(
                new CannotAcquireLockException("could not obtain lock"),
                new PessimisticLockingFailureException("lock failure"),
                new DeadlockLoserDataAccessException("deadlock detected", new SQLException("40P01")),
                new OptimisticLockingFailureException("stale"),
                new OptimisticLockException("stale"),
                new PessimisticLockException("lock"),
                new LockTimeoutException("lock timeout"));
    }

    @ParameterizedTest
    @MethodSource("lockFailures")
    void everyWayOfLosingTheRaceBecomesTheTypedConflictCarryingTheCause(RuntimeException failure) {
        // Arrange
        PlanId id = PlanId.generate();
        org.junit.jupiter.api.function.Executable act = () -> WriteSupport.translatingConflicts(() -> {
            throw failure;
        }, () -> new PlanModifiedConcurrentlyException(id));

        // Act
        PlanModifiedConcurrentlyException ex = assertThrows(PlanModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.planId()).isEqualTo(id);
        assertThat(ex.getCause()).isSameAs(failure);
    }

    @Test
    void anUnrelatedFailureAndAnAlreadyTypedConflictPassThroughUntouched() {
        // Arrange
        IllegalStateException unrelated = new IllegalStateException("boom");
        PlanModifiedConcurrentlyException typed = new PlanModifiedConcurrentlyException(PlanId.generate());
        org.junit.jupiter.api.function.Executable throwsUnrelated = () -> WriteSupport.translatingConflicts(() -> {
            throw unrelated;
        }, () -> new PlanModifiedConcurrentlyException(PlanId.generate()));
        org.junit.jupiter.api.function.Executable throwsTyped = () -> WriteSupport.translatingConflicts(() -> {
            throw typed;
        }, () -> new PlanModifiedConcurrentlyException(PlanId.generate()));

        // Act
        IllegalStateException first = assertThrows(IllegalStateException.class, throwsUnrelated);
        PlanModifiedConcurrentlyException second = assertThrows(PlanModifiedConcurrentlyException.class, throwsTyped);

        // Assert
        assertThat(first).isSameAs(unrelated);
        assertThat(second).isSameAs(typed);
    }

    @Test
    void aSuccessfulActionReturnsItsResult() {
        // Arrange
        // (nothing to set up)

        // Act
        String result = WriteSupport.translatingConflicts(() -> "ok", () -> new PlanModifiedConcurrentlyException(PlanId.generate()));

        // Assert
        assertThat(result).isEqualTo("ok");
    }
}
