package com.regivolley.api.infrastructure.web.dto;

/** The plan types a client may name, as the wire spells them (RN-13). Mapped to the domain's types by an exhaustive switch. */
public enum PlanTypeName {
    MONTHLY_UNLIMITED,
    MONTHLY_N_PER_WEEK,
    PACK,
    SINGLE_SESSION
}
