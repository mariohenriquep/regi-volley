package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/** US-05/US-06: PENDING -> APPROVED | REJECTED; both decisions are final. */
public enum JoinRequestStatus implements ValueObject {
    PENDING,
    APPROVED,
    REJECTED
}
