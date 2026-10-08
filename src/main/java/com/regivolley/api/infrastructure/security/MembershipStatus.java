package com.regivolley.api.infrastructure.security;

/**
 * Whether a user's link to a member is usable (the status of {@code membership}, 26b): PENDING until the emailed link is
 * consumed, CONFIRMED afterwards (threat model D-11).
 */
public enum MembershipStatus {
    PENDING,
    CONFIRMED
}
