package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.CreditUsage;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.infrastructure.persistence.entity.LevelRef;
import com.regivolley.api.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.SubscriptionJpaEntity.UsageRow;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Translates a {@link Subscription} to and from {@link SubscriptionJpaEntity}. */
public final class SubscriptionPersistenceMapper {

    private SubscriptionPersistenceMapper() {
    }

    /** Rebuilds the aggregate through its factory, which re-checks its invariants. */
    public static Subscription toDomain(SubscriptionJpaEntity entity) {
        Set<LevelId> allowedLevels = entity.getAllowedLevels().stream()
                .map(ref -> new LevelId(ref.getLevelId()))
                .collect(Collectors.toSet());
        PlanTerms terms = new PlanTerms(PlanType.valueOf(entity.getPlanType()), entity.getSessionsPerWeek(),
                entity.getCredits(), allowedLevels);
        List<CreditUsage> usages = entity.getUsages().stream()
                .map(row -> new CreditUsage(new BookingId(row.getBookingId()), row.getSessionDate()))
                .toList();
        return SubscriptionFactory.reconstitute(
                new SubscriptionId(entity.getId()),
                new AssociationId(entity.getAssociationId()),
                new MemberId(entity.getMemberId()),
                new PlanId(entity.getPlanId()),
                terms,
                Money.ofCents(entity.getPriceCents()),
                entity.getStartDate(),
                entity.getEndDate(),
                PaymentStatus.valueOf(entity.getPaymentStatus()),
                usages,
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    /** Copies the subscription onto {@code entity} (new, or loaded and managed). The version stays with the persistence layer. */
    public static void apply(Subscription subscription, SubscriptionJpaEntity entity) {
        UUID associationId = subscription.associationId().value();
        PlanTerms terms = subscription.terms();
        entity.setId(subscription.id().value());
        entity.setAssociationId(associationId);
        entity.setMemberId(subscription.memberId().value());
        entity.setPlanId(subscription.planId().value());
        entity.setPriceCents(subscription.price().cents());
        entity.setPlanType(terms.type().name());
        entity.setSessionsPerWeek(terms.sessionsPerWeek());
        entity.setCredits(terms.credits());
        entity.setStartDate(subscription.startDate());
        entity.setEndDate(subscription.endDate());
        entity.setPaymentStatus(subscription.paymentStatus().name());
        CollectionSync.replace(entity.getAllowedLevels(), terms.allowedLevels().stream()
                .map(level -> new LevelRef(associationId, level.value()))
                .collect(Collectors.toSet()));
        CollectionSync.replace(entity.getUsages(), subscription.usages().stream()
                .map(usage -> new UsageRow(associationId, usage.bookingId().value(), usage.sessionDate()))
                .toList());
    }
}
