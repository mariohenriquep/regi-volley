package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanTerms;

import java.util.Objects;

/** An administrator creates a plan (US-19, RN-13, RN-14). {@code validityDays} is required for packs and single sessions and absent for monthly plans. */
public record CreatePlanCommand(Actor actor, String name, PlanTerms terms, Money price, Integer validityDays) {

    public CreatePlanCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(terms, "terms must not be null");
        Objects.requireNonNull(price, "price must not be null");
    }
}
