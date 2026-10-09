package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.infrastructure.persistence.entity.JoinRequestJpaEntity;
import com.regivolley.api.infrastructure.persistence.mapper.JoinRequestPersistenceMapper;
import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** {@link JoinRequestRepository} on Spring Data JPA. Nothing here logs a name, email or phone. */
@Component
public class JoinRequestRepositoryAdapter implements JoinRequestRepository {

    private static final String PENDING_EMAIL_INDEX = "uq_join_requests_pending_email";

    private final JoinRequestJpaRepository requests;
    private final EntityManager entityManager;

    public JoinRequestRepositoryAdapter(JoinRequestJpaRepository requests, EntityManager entityManager) {
        this.requests = requests;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JoinRequest> findById(AssociationId associationId, JoinRequestId id) {
        return requests.findByIdAndAssociationId(id.value(), associationId.value())
                .map(JoinRequestPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JoinRequest> findPendingByEmail(AssociationId associationId, EmailAddress email) {
        return requests.findByAssociationIdAndEmailAndStatus(associationId.value(), email.value(),
                        JoinRequestStatus.PENDING.name())
                .map(JoinRequestPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequest> findPending(AssociationId associationId) {
        return requests.findByAssociationIdAndStatusOrderByRequestedAtAscIdAsc(
                        associationId.value(), JoinRequestStatus.PENDING.name(), PageRequest.of(0, MAX_PENDING_LISTED)).stream()
                .map(JoinRequestPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequest> findPendingRequestedBefore(AssociationId associationId, Instant cutoff, int limit) {
        return requests.findByAssociationIdAndStatusAndRequestedAtBeforeOrderByRequestedAtAscIdAsc(
                        associationId.value(), JoinRequestStatus.PENDING.name(), cutoff, PageRequest.of(0, limit)).stream()
                .map(JoinRequestPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public JoinRequest save(JoinRequest request) {
        try {
            return WriteSupport.translatingConflicts(() -> JoinRequestPersistenceMapper.toDomain(
                    WriteSupport.write(requests, entityManager,
                            requests.findForUpdateByIdAndAssociationId(request.id().value(), request.associationId().value()),
                            request.version(), JoinRequestJpaEntity::new,
                            entity -> JoinRequestPersistenceMapper.apply(request, entity),
                            JoinRequestJpaEntity::getVersion, () -> conflict(request))),
                    () -> conflict(request));
        } catch (DataIntegrityViolationException e) {
            if (WriteSupport.violates(e, PENDING_EMAIL_INDEX)) {
                // The cause is dropped on purpose: it could quote the email address.
                throw new JoinRequestNotPossibleException();
            }
            throw e;
        }
    }

    private static JoinRequestModifiedConcurrentlyException conflict(JoinRequest request) {
        return new JoinRequestModifiedConcurrentlyException(request.id());
    }
}
