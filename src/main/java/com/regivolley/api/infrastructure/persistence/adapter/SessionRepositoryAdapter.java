package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.infrastructure.persistence.entity.SessionJpaEntity;
import com.regivolley.api.infrastructure.persistence.mapper.SessionPersistenceMapper;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link SessionRepository} on Spring Data JPA. The last seat is protected here (architecture.md
 * section 10): a save locks the session row first, then every save of an existing session moves its
 * version forward by one, bookings or not, so two requests that loaded the same version cannot both
 * be stored.
 *
 * <p>Writes join the caller's transaction or start their own. A {@link SessionModifiedConcurrentlyException}
 * leaves that transaction rolled back, so a retry must start a new one.
 */
@Component
public class SessionRepositoryAdapter implements SessionRepository {

    /** Two live bookings of one member in a session; the race the version check did not see first. */
    private static final String LIVE_BOOKING_INDEX = "uq_bookings_live_member_session";
    /** The same occurrence of a group generated twice by concurrent runs. */
    private static final String GENERATED_START_INDEX = "uq_sessions_group_starts_at";

    private final SessionJpaRepository sessions;
    private final EntityManager entityManager;

    public SessionRepositoryAdapter(SessionJpaRepository sessions, EntityManager entityManager) {
        this.sessions = sessions;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findById(AssociationId associationId, SessionId id) {
        return sessions.findByIdAndAssociationId(id.value(), associationId.value())
                .map(SessionPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Session> findStartingBetween(AssociationId associationId, Instant from, Instant toExclusive) {
        return toDomain(sessions.findStartingBetween(associationId.value(), from, toExclusive));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Session> findByTrainingGroupStartingBetween(AssociationId associationId,
                                                            TrainingGroupId trainingGroupId,
                                                            Instant from, Instant toExclusive) {
        return toDomain(sessions.findByTrainingGroupStartingBetween(
                associationId.value(), trainingGroupId.value(), from, toExclusive));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Session> findWithLiveBookingOverlapping(AssociationId associationId, MemberId memberId,
                                                        Instant from, Instant toExclusive) {
        return toDomain(sessions.findWithLiveBookingOverlapping(
                associationId.value(), memberId.value(), from, toExclusive));
    }

    @Override
    @Transactional
    public Session save(Session session) {
        try {
            return WriteSupport.translatingConflicts(() -> SessionPersistenceMapper.toDomain(
                    WriteSupport.write(sessions, entityManager,
                            sessions.findForUpdateByIdAndAssociationId(session.id().value(), session.associationId().value()),
                            session.version(), SessionJpaEntity::new,
                            entity -> SessionPersistenceMapper.apply(session, entity),
                            SessionJpaEntity::getVersion, () -> conflict(session))),
                    () -> conflict(session));
        } catch (DataIntegrityViolationException e) {
            if (WriteSupport.violates(e, LIVE_BOOKING_INDEX) || WriteSupport.violates(e, GENERATED_START_INDEX)) {
                SessionModifiedConcurrentlyException conflict = conflict(session);
                conflict.initCause(e);
                throw conflict;
            }
            throw e;
        }
    }

    private static SessionModifiedConcurrentlyException conflict(Session session) {
        return new SessionModifiedConcurrentlyException(session.id());
    }

    private static List<Session> toDomain(List<SessionJpaEntity> entities) {
        return entities.stream().map(SessionPersistenceMapper::toDomain).toList();
    }
}
