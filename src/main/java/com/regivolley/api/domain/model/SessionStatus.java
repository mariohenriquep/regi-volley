package com.regivolley.api.domain.model;

/** RN-05: SCHEDULED -> COMPLETED or SCHEDULED -> CANCELLED; both end states are final. */
public enum SessionStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED
}
