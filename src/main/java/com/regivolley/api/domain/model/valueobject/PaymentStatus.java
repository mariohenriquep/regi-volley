package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/** RN-18: PENDING -> PAID | OVERDUE and OVERDUE -> PAID; PAID is final. */
public enum PaymentStatus implements ValueObject {
    PENDING,
    PAID,
    OVERDUE
}
