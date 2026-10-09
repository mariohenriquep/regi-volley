package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.regivolley.api.infrastructure.persistence.mapper.SubscriptionPersistenceMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@link SubscriptionRepository} on Spring Data JPA. Every save of an existing subscription moves
 * its version forward by one (usages included), so two concurrent charges against the same balance
 * cannot both be stored. A conflict leaves the transaction rolled back: retry in a new one.
 */
@Component
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository subscriptions;
    private final EntityManager entityManager;

    public SubscriptionRepositoryAdapter(SubscriptionJpaRepository subscriptions, EntityManager entityManager) {
        this.subscriptions = subscriptions;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> findById(AssociationId associationId, SubscriptionId id) {
        return subscriptions.findByIdAndAssociationId(id.value(), associationId.value())
                .map(SubscriptionPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Subscription> findByMember(AssociationId associationId, MemberId memberId) {
        return subscriptions.findByAssociationIdAndMemberIdOrderByStartDateAscIdAsc(
                        associationId.value(), memberId.value()).stream()
                .map(SubscriptionPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Subscription> findByPaymentStatus(AssociationId associationId, PaymentStatus status, LocalDate endingFrom,
                                                  LocalDate endingTo) {
        return subscriptions.findByAssociationIdAndPaymentStatusAndEndDateBetweenOrderByEndDateAscIdAsc(
                        associationId.value(), status.name(), endingFrom, endingTo).stream()
                .map(SubscriptionPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Subscription save(Subscription subscription) {
        return WriteSupport.translatingConflicts(() -> SubscriptionPersistenceMapper.toDomain(
                WriteSupport.write(subscriptions, entityManager,
                        subscriptions.findForUpdateByIdAndAssociationId(
                                subscription.id().value(), subscription.associationId().value()),
                        subscription.version(), SubscriptionJpaEntity::new,
                        entity -> SubscriptionPersistenceMapper.apply(subscription, entity),
                        SubscriptionJpaEntity::getVersion, () -> conflict(subscription))),
                () -> conflict(subscription));
    }

    private static SubscriptionModifiedConcurrentlyException conflict(Subscription subscription) {
        return new SubscriptionModifiedConcurrentlyException(subscription.id());
    }
}
