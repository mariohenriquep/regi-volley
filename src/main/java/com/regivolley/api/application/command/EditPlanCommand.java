package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;

import java.util.Objects;

/** An administrator edits a plan (US-19); subscriptions already sold keep the terms they snapshotted. */
public record EditPlanCommand(Actor actor, PlanId planId, String name, PlanTerms terms, Money price, Integer validityDays) {

    public EditPlanCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(terms, "terms must not be null");
        Objects.requireNonNull(price, "price must not be null");
    }
}
