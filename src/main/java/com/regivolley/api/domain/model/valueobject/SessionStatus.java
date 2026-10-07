package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/** RN-05: SCHEDULED -> COMPLETED or SCHEDULED -> CANCELLED; both end states are final. */
public enum SessionStatus implements ValueObject {
    SCHEDULED,
    COMPLETED,
    CANCELLED
}
