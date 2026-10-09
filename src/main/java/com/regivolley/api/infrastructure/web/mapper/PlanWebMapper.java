package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.CreatePlanCommand;
import com.regivolley.api.application.command.EditPlanCommand;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.infrastructure.web.dto.PlanRequest;
import com.regivolley.api.infrastructure.web.dto.PlanResponse;
import com.regivolley.api.infrastructure.web.dto.PlanTypeName;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Plans (US-19, RN-13). Which fields go with which type is the domain's rule ({@link PlanTerms}); this only carries them across. */
public final class PlanWebMapper {

    private PlanWebMapper() {
    }

    public static CreatePlanCommand createCommand(Actor actor, PlanRequest body) {
        return new CreatePlanCommand(actor, body.name(), terms(body), Money.ofCents(body.priceCents()), body.validityDays());
    }

    public static EditPlanCommand editCommand(Actor actor, UUID planId, PlanRequest body) {
        return new EditPlanCommand(actor, PlanId.of(planId), body.name(), terms(body), Money.ofCents(body.priceCents()),
                body.validityDays());
    }

    public static PlanResponse toResponse(Plan plan) {
        return new PlanResponse(plan.id().value(), plan.name(), plan.type().name(), plan.terms().sessionsPerWeek(),
                plan.terms().credits(), plan.allowedLevels().stream().map(LevelId::value).sorted().toList(), plan.price().cents(),
                plan.validityDays().isPresent() ? plan.validityDays().getAsInt() : null);
    }

    private static PlanTerms terms(PlanRequest body) {
        Set<LevelId> allowed = body.allowedLevelIds() == null ? Set.of()
                : body.allowedLevelIds().stream().map(LevelId::of).collect(Collectors.toSet());
        return new PlanTerms(type(body.type()), body.sessionsPerWeek(), body.credits(), allowed);
    }

    /** Every wire spelling has its domain type; a new value on either side stops the build here. */
    static PlanType type(PlanTypeName name) {
        return switch (name) {
            case MONTHLY_UNLIMITED -> PlanType.MONTHLY_UNLIMITED;
            case MONTHLY_N_PER_WEEK -> PlanType.MONTHLY_N_PER_WEEK;
            case PACK -> PlanType.PACK;
            case SINGLE_SESSION -> PlanType.SINGLE_SESSION;
        };
    }
}
