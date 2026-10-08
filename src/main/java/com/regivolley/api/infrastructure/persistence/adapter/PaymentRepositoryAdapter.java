package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.PaymentModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * {@link PaymentRepository} on Spring Data JPA. Append-only (RN-19): payments are inserted with a plain
 * {@code persist} (never a merge, so an existing id cannot be overwritten) and nothing here updates or deletes;
 * the table's trigger enforces the same for any other writer.
 */
@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    /** The only ways a correct, serialised caller can lose: the same payment reversed twice, a reversal across subscriptions, an id that exists. */
    private static final List<String> CONCURRENCY_CONSTRAINTS =
            List.of("uq_payments_reversal_of", "fk_payments_reversal_of", "payments_pkey");

    private final PaymentJpaRepository payments;
    private final EntityManager entityManager;

    public PaymentRepositoryAdapter(PaymentJpaRepository payments, EntityManager entityManager) {
        this.payments = payments;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(AssociationId associationId, PaymentId id) {
        return payments.findByIdAndAssociationId(id.value(), associationId.value()).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findBySubscription(AssociationId associationId, SubscriptionId subscriptionId) {
        return payments.findByAssociationIdAndSubscriptionIdOrderByRecordedAtAscIdAsc(
                        associationId.value(), subscriptionId.value()).stream()
                .map(PaymentPersistenceMapper::toDomain)
                .toList();
    }

    private static PaymentModifiedConcurrentlyException conflict(Payment payment, Exception cause) {
        PaymentModifiedConcurrentlyException translated = new PaymentModifiedConcurrentlyException(payment.id());
        translated.initCause(cause);
        return translated;
    }

    @Override
    @Transactional
    public Payment add(Payment payment) {
        try {
            entityManager.persist(PaymentPersistenceMapper.toNewEntity(payment));
            entityManager.flush();
        } catch (EntityExistsException e) {
            throw conflict(payment, e);
        } catch (ConstraintViolationException e) {
            if (CONCURRENCY_CONSTRAINTS.stream().anyMatch(name -> WriteSupport.violates(e, name))) {
                throw conflict(payment, e);
            }
            // Anything else (an unknown subscription, another tenant's) is a bug or an attack, not a lost race.
            throw new DataIntegrityViolationException("A payment violates a database constraint", e);
        }
        return payment;
    }
}
