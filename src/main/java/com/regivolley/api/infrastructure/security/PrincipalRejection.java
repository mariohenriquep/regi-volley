package com.regivolley.api.infrastructure.security;

/** Why a validly signed token was refused. Goes to the log by ids; the client always sees the same 401. */
public enum PrincipalRejection {
    MALFORMED_CLAIMS,
    UNKNOWN_ACCOUNT,
    ACCOUNT_DISABLED,
    STAMP_MISMATCH,
    MEMBERSHIP_NOT_CONFIRMED,
    MEMBERSHIP_MISMATCH,
    MEMBER_NOT_FOUND,
    MEMBER_INACTIVE,
    MEMBER_ANONYMISED
}
