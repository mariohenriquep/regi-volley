package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.AttemptThrottle;

/** {@link AttemptThrottle} on the shared {@link RateLimiter}: login 5 per 15 minutes, reset 3 per hour and join 3 per day per association, per email hash. */
public class RateLimitingAttemptThrottle implements AttemptThrottle {

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

    @Override
    public void checkJoinRequest(String shortName, String email) {
        limiter.check(RateLimitRule.JOIN_EMAIL, RateLimiter.joinKey(shortName, email));
    }
}
