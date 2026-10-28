package com.regivolley.api.infrastructure.web.dto;

/** The member statuses a client may filter by, as the wire spells them. Anything else is a 400 before any use case runs. */
public enum MemberStatusName {
    ACTIVE,
    INACTIVE
}
