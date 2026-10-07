package com.regivolley.api.domain.model;

/** US-05/US-06: PENDING -> APPROVED | REJECTED; both decisions are final. */
public enum JoinRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
