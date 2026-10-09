package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;

/**
 * {@link AttemptThrottle} on the shared {@link RateLimiter}: login 5 per 15 minutes, reset 3 per hour and join 3 per day per association, per
 * email hash; and an administrator's resends of activation links: 30 an hour per association, 3 per member, and 6 mails an hour per address.
 */
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

    @Override
    public void checkActivationLinkResend(AssociationId associationId, MemberId memberId) {
        limiter.check(RateLimitRule.ASSOCIATION_RESEND, associationId.toString());
        limiter.check(RateLimitRule.ACTIVATION_RESEND, RateLimiter.memberKey(associationId, memberId));
    }

    @Override
    public void checkLinkMailAddress(EmailAddress address) {
        limiter.check(RateLimitRule.LINK_MAIL_PER_ADDRESS, RateLimiter.emailKey(address.value()));
    }
}
