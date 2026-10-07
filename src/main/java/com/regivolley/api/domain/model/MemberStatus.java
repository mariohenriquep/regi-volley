package com.regivolley.api.domain.model;

/**
 * Whether a member may book at all (RN-06). Owned by the {@link Member} aggregate: INACTIVE
 * (US-08, or anonymised) members are rejected by booking eligibility.
 */
public enum MemberStatus {
    ACTIVE,
    INACTIVE
}
