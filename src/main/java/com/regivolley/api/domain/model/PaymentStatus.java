package com.regivolley.api.domain.model;

/** RN-18: PENDING -> PAID | OVERDUE and OVERDUE -> PAID; PAID is final. */
public enum PaymentStatus {
    PENDING,
    PAID,
    OVERDUE
}
