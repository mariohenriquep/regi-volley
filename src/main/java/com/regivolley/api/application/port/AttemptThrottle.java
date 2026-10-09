package com.regivolley.api.application.port;

/**
 * Outbound port for the per-key rate limits that need the request's content, keyed by a hash of an email (alone, or with the association) (threat model D-9): login 5 per 15 minutes, password-reset requests 3 per hour, join requests 3 per day per association.
 * Keyed by a hash of the address, so it counts unknown addresses exactly like known ones. The per-IP limits belong to the web
 * layer's filter, which knows the connection; this one needs the body.
 */
public interface AttemptThrottle {

    /** @throws com.regivolley.api.application.exception.RateLimitExceededException if this email was tried too often */
    void checkLogin(String email);

    /** @throws com.regivolley.api.application.exception.RateLimitExceededException if this email asked too often */
    void checkPasswordResetRequest(String email);

    /**
     * Join requests: 3 per day per association and email (D-9). Counted before anything is looked up and the same for every outcome
     * (new, already a member, request pending), so the limit itself says nothing about the address.
     *
     * @throws com.regivolley.api.application.exception.RateLimitExceededException if this email asked this association too often
     */
    void checkJoinRequest(String shortName, String email);
}
