package com.regivolley.api.infrastructure.web.dto;

/** The roles a client may name in a path, as the wire spells them. Anything else is a 400 before any use case runs. */
public enum MemberRoleName {
    MEMBER,
    COACH,
    ADMIN
}
