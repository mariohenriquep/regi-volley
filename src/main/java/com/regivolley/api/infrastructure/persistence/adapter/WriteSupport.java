package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.AggregateModifiedConcurrentlyException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The write protocol shared by every versioned aggregate (architecture.md section 10).
 *
 * <p>Saving an existing aggregate always goes: lock the root row (before any child row is touched, so
 * two writers queue up on the root instead of deadlocking on children) -> compare the stored version
 * with the one the caller loaded -> apply the domain state -> flush -> make sure the version moved by
 * exactly one. Any failure to win that race, including a lock that cannot be obtained, surfaces as the
 * aggregate's own {@link AggregateModifiedConcurrentlyException}, never as a raw persistence error.
 */
final class WriteSupport {

    private WriteSupport() {
    }

    /**
     * Inserts or updates one aggregate and returns the stored entity.
     *
     * @param lockedRoot    the stored root row, already locked for update (empty if there is none in this association)
     * @param domainVersion the version the aggregate was loaded with (0 for a new one)
     * @param fresh         creates an empty entity for an insert
     * @param apply         copies the domain state onto the entity (scalars and child rows, not the version)
     * @param versionOf     the entity's current version
     * @param conflict      builds the typed conflict exception
     * @throws AggregateModifiedConcurrentlyException if the stored version differs, or the aggregate has a
     *         version but no stored row in this association (it is gone, or belongs to nobody here)
     */
    static <E> E write(JpaRepository<E, ?> repository, EntityManager entityManager, Optional<E> lockedRoot,
                       long domainVersion, Supplier<E> fresh, Consumer<E> apply, Function<E, Long> versionOf,
                       Supplier<? extends AggregateModifiedConcurrentlyException> conflict) {
        if (lockedRoot.isEmpty()) {
            if (domainVersion > 0) {
                throw conflict.get();
            }
            E entity = fresh.get();
            apply.accept(entity);
            return repository.saveAndFlush(entity);
        }
        E entity = lockedRoot.get();
        long loadedVersion = versionOf.apply(entity);
        if (loadedVersion != domainVersion) {
            throw conflict.get();
        }
        apply.accept(entity);
        repository.flush();
        ensureVersionIncremented(entityManager, entity, loadedVersion, versionOf.apply(entity));
        return entity;
    }

    /**
     * Runs {@code action}, turning every way of losing a race for the aggregate (a version that moved, a
     * lock that could not be obtained in time, a deadlock victim) into the typed conflict exception.
     */
    static <T> T translatingConflicts(Supplier<T> action,
                                      Supplier<? extends AggregateModifiedConcurrentlyException> conflict) {
        try {
            return action.get();
        } catch (ConcurrencyFailureException | OptimisticLockException | PessimisticLockException
                 | LockTimeoutException e) {
            AggregateModifiedConcurrentlyException translated = conflict.get();
            translated.initCause(e);
            throw translated;
        }
    }

    /**
     * Makes sure a flushed, already stored entity moved its version forward by exactly one.
     * Hibernate does that itself when a column of the entity changed; when only child rows changed
     * (bookings of a session, levels of an association) it does not, so the version is bumped
     * explicitly. {@code PESSIMISTIC_FORCE_INCREMENT} runs {@code UPDATE ... SET version = n + 1
     * WHERE id = ? AND version = n} right now. The root row is already locked by this transaction, so it
     * neither waits nor loses.
     *
     * @throws jakarta.persistence.OptimisticLockException if somebody else already moved the version
     */
    static void ensureVersionIncremented(EntityManager entityManager, Object entity, long versionBeforeFlush,
                                         long versionAfterFlush) {
        if (versionAfterFlush == versionBeforeFlush) {
            entityManager.lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
    }

    /** Whether the failure is a violation of the named database constraint or unique index. */
    static boolean violates(Throwable failure, String constraintName) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (cause.getMessage() != null && cause.getMessage().contains("\"" + constraintName + "\"")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
