package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;

/**
 * Creates and reconstitutes {@link Plan}s: the only place a plan is born. {@link Plan}'s constructor checks every
 * invariant, so neither path can produce an invalid plan. Stateless, so its methods are static.
 */
public final class PlanFactory {

    private PlanFactory() {
    }

    /**
     * A new plan (RN-13, US-19) with a generated id, at version 0.
     *
     * @param validityDays required (at least 1) for PACK and SINGLE_SESSION, absent for monthly plans
     */
    public static Plan create(AssociationId associationId, String name, PlanTerms terms, Money price,
                              Integer validityDays) {
        return new Plan(PlanId.generate(), associationId, name, terms, price, validityDays, 0L);
    }

    /**
     * Rebuilds a plan from persisted data.
     *
     * @param version the optimistic-lock version it was loaded with (architecture.md section 10)
     */
    public static Plan reconstitute(PlanId id, AssociationId associationId, String name, PlanTerms terms,
                                    Money price, Integer validityDays, long version) {
        return new Plan(id, associationId, name, terms, price, validityDays, version);
    }
}
