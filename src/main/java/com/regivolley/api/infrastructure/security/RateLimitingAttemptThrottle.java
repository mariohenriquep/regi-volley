package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.CredentialAttemptThrottle;

/** {@link CredentialAttemptThrottle} on the shared {@link RateLimiter}: login 5 per 15 minutes and reset 3 per hour, per email hash. */
public class RateLimitingAttemptThrottle implements CredentialAttemptThrottle {

    private final RateLimiter limiter;

    public RateLimitingAttemptThrottle(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public void checkLogin(String email) {
        limiter.check(RateLimitRule.LOGIN_EMAIL, RateLimiter.emailKey(email));
    }

    @Override
    public void checkPasswordResetRequest(String email) {
        limiter.check(RateLimitRule.RESET_EMAIL, RateLimiter.emailKey(email));
    }
}
