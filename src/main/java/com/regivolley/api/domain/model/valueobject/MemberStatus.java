package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/**
 * Whether a member may book at all (RN-06). Owned by the {@code Member} aggregate: INACTIVE
 * (US-08, or anonymised) members are rejected by booking eligibility.
 */
public enum MemberStatus implements ValueObject {
    ACTIVE,
    INACTIVE
}
