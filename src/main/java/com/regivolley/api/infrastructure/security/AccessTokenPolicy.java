package com.regivolley.api.infrastructure.security;

import java.time.Duration;

/**
 * The fixed terms of the access token (threat model D-4): who issues it, who it is for, how long it lives, how much clock
 * difference is tolerated and the names of the claims. Claims are ids only: no roles (D-5), no personal data.
 */
public final class AccessTokenPolicy {

    public static final String ISSUER = "regi-volley-api";
    public static final String AUDIENCE = "regi-volley-web";
    public static final Duration TTL = Duration.ofMinutes(10);
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    /** The association (tenant) of the selected membership. */
    public static final String CLAIM_ASSOCIATION = "aid";
    /** The member of the selected membership. */
    public static final String CLAIM_MEMBER = "mid";
    /** The user's security stamp when the token was issued; compared with the stored one on every request (D-6). */
    public static final String CLAIM_SECURITY_STAMP = "sv";

    private AccessTokenPolicy() {
    }
}
