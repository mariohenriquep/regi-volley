package com.regivolley.api.infrastructure.web.dto;

/** The payment statuses a client may filter by, as the wire spells them. Anything else is a 400 before any use case runs. */
public enum PaymentStatusName {
    PENDING,
    PAID,
    OVERDUE
}
