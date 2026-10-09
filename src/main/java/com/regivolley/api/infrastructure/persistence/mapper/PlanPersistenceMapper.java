package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.infrastructure.persistence.entity.LevelRef;
import com.regivolley.api.infrastructure.persistence.entity.PlanJpaEntity;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Translates a {@link Plan} to and from {@link PlanJpaEntity}. */
public final class PlanPersistenceMapper {

    private PlanPersistenceMapper() {
    }

    /** Rebuilds the aggregate through its factory, which re-checks its invariants. */
    public static Plan toDomain(PlanJpaEntity entity) {
        Set<LevelId> allowedLevels = entity.getAllowedLevels().stream()
                .map(ref -> new LevelId(ref.getLevelId()))
                .collect(Collectors.toSet());
        PlanTerms terms = new PlanTerms(PlanType.valueOf(entity.getPlanType()), entity.getSessionsPerWeek(),
                entity.getCredits(), allowedLevels);
        return PlanFactory.reconstitute(
                new PlanId(entity.getId()),
                new AssociationId(entity.getAssociationId()),
                entity.getName(),
                terms,
                Money.ofCents(entity.getPriceCents()),
                entity.getValidityDays(),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    /** Copies the plan onto {@code entity} (new, or loaded and managed). The version stays with the persistence layer. */
    public static void apply(Plan plan, PlanJpaEntity entity) {
        UUID associationId = plan.associationId().value();
        PlanTerms terms = plan.terms();
        entity.setId(plan.id().value());
        entity.setAssociationId(associationId);
        entity.setName(plan.name());
        entity.setPlanType(terms.type().name());
        entity.setSessionsPerWeek(terms.sessionsPerWeek());
        entity.setCredits(terms.credits());
        entity.setPriceCents(plan.price().cents());
        entity.setValidityDays(plan.validityDays().isPresent() ? plan.validityDays().getAsInt() : null);
        CollectionSync.replace(entity.getAllowedLevels(), terms.allowedLevels().stream()
                .map(level -> new LevelRef(associationId, level.value()))
                .collect(Collectors.toSet()));
    }
}
