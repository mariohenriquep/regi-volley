package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.port.Notifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class UnitOfWorkTest {

    private final DirectTransactions transactions = new DirectTransactions();
    private UnitOfWork unitOfWork;

    @Mock
    private Notifier notifier;

    @BeforeEach
    void setUp() {
        unitOfWork = new UnitOfWork(transactions);
    }

    private static SessionModifiedConcurrentlyException conflict() {
        return new SessionModifiedConcurrentlyException(SessionId.generate());
    }

    @Test
    void runsTheWorkOnceInANewTransactionWhenNothingConflicts() {
        // Arrange
        AtomicInteger calls = new AtomicInteger();

        // Act
        int result = unitOfWork.retrying(calls::incrementAndGet);

        // Assert
        assertThat(result).isEqualTo(1);
        assertThat(transactions.opened()).isEqualTo(1);
    }

    @Test
    void retriesAConflictInAFreshTransactionAndReturnsTheLaterSuccess() {
        // Arrange
        AtomicInteger calls = new AtomicInteger();

        // Act
        String result = unitOfWork.retrying(() -> {
            if (calls.incrementAndGet() < 3) {
                throw calls.get() == 1 ? conflict() : new SubscriptionModifiedConcurrentlyException(SubscriptionId.generate());
            }
            return "done";
        });

        // Assert
        assertThat(result).isEqualTo("done");
        assertThat(transactions.opened()).isEqualTo(3);
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttemptsAndRethrowsTheLastConflict() {
        // Arrange
        AtomicInteger calls = new AtomicInteger();
        Executable act = () -> unitOfWork.retrying(() -> {
            calls.incrementAndGet();
            throw conflict();
        });

        // Act
        SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertThat(calls.get()).isEqualTo(UnitOfWork.MAX_ATTEMPTS);
        assertThat(transactions.opened()).isEqualTo(UnitOfWork.MAX_ATTEMPTS);
    }

    @Test
    void doesNotRetryARuleViolation() {
        // Arrange
        AtomicInteger calls = new AtomicInteger();
        Executable act = () -> unitOfWork.retrying(() -> {
            calls.incrementAndGet();
            throw new NotAllowedException("do that");
        });

        // Act
        NotAllowedException ex = assertThrows(NotAllowedException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("do that");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void sendsTheNotificationsOfTheCommittedAttemptOnceAfterRetries() {
        // Arrange
        AssociationId association = AssociationId.generate();
        MemberId won = MemberId.generate();
        SessionId session = SessionId.generate();
        AtomicInteger calls = new AtomicInteger();

        // Act
        String result = unitOfWork.retryingAndNotify(() -> {
            if (calls.incrementAndGet() == 1) {
                throw conflict();
            }
            return Outcome.of("second", List.<Consumer<Notifier>>of(n -> n.bookingPromoted(association, session, won)));
        }, notifier);

        // Assert
        assertThat(result).isEqualTo("second");
        verify(notifier).bookingPromoted(association, session, won);
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void sendsNothingWhenEveryAttemptConflicts() {
        // Arrange
        Executable act = () -> unitOfWork.retryingAndNotify(() -> {
            throw conflict();
        }, notifier);

        // Act
        SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verifyNoInteractions(notifier);
    }

    @Test
    void aFailingNotifierNeverUndoesTheCommittedWork() {
        // Arrange
        AssociationId association = AssociationId.generate();
        MemberId member = MemberId.generate();
        SessionId session = SessionId.generate();
        doThrow(new IllegalStateException("mail server down")).when(notifier).bookingPromoted(association, session, member);

        // Act
        String result = unitOfWork.retryingAndNotify(() -> Outcome.of("committed",
                List.<Consumer<Notifier>>of(n -> n.bookingPromoted(association, session, member))), notifier);

        // Assert
        assertThat(result).isEqualTo("committed");
        verify(notifier).bookingPromoted(association, session, member);
    }
}
