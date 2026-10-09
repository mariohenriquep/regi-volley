package com.regivolley.api.infrastructure.web.dto;

/** The payment methods a client may name, as the wire spells them. Mapped to the domain's methods by an exhaustive switch. */
public enum PaymentMethodName {
    CASH,
    TRANSFER,
    MB_WAY
}
