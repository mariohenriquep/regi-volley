package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/** RN-18: PENDING -> PAID | OVERDUE and OVERDUE -> PAID; PAID goes back to PENDING only when a payment is reversed (RN-19). */
public enum PaymentStatus implements ValueObject {
    PENDING,
    PAID,
    OVERDUE
}
