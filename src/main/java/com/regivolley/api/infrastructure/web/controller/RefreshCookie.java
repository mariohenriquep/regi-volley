package com.regivolley.api.infrastructure.web.controller;

import org.springframework.http.ResponseCookie;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The refresh token's cookie (threat model D-7a): {@code __Secure-rt}, {@code HttpOnly} (script cannot read it), {@code Secure},
 * {@code SameSite=Strict} (never sent on a cross-site request) and scoped to {@code /api/v1/auth}, so only the credential
 * endpoints ever receive it. No {@code Domain} attribute: it belongs to the exact host that set it. Lives as long as the token.
 */
final class RefreshCookie {

    /** {@code __Secure-} makes browsers accept the cookie only if it is {@code Secure}. */
    static final String NAME = "__Secure-rt";
    /** The cookie is sent to the credential endpoints only. */
    static final String PATH = "/api/v1/auth";

    private RefreshCookie() {
    }

    /** The {@code Set-Cookie} value that stores the token until {@code expiresAt}. */
    static String issue(String token, Instant expiresAt, Clock clock) {
        Duration maxAge = Duration.between(clock.instant(), expiresAt);
        return build(token, maxAge.isNegative() ? Duration.ZERO : maxAge);
    }

    /** The {@code Set-Cookie} value that deletes it. */
    static String clear() {
        return build("", Duration.ZERO);
    }

    private static String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
