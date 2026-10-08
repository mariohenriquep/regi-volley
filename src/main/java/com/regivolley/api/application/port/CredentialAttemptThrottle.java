package com.regivolley.api.application.port;

/**
 * Outbound port for the per-email rate limits (threat model D-9): login 5 per 15 minutes, password-reset requests 3 per hour.
 * Keyed by a hash of the address, so it counts unknown addresses exactly like known ones. The per-IP limits belong to the web
 * layer's filter, which knows the connection; this one needs the body.
 */
public interface CredentialAttemptThrottle {

    /** @throws com.regivolley.api.application.exception.RateLimitExceededException if this email was tried too often */
    void checkLogin(String email);

    /** @throws com.regivolley.api.application.exception.RateLimitExceededException if this email asked too often */
    void checkPasswordResetRequest(String email);
}
