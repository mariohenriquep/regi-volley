package com.regivolley.api.application.port;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;

/**
 * Outbound port for the per-key rate limits that need the request's content, keyed by a hash of an email (alone, or with the association) (threat model D-9): login 5 per 15 minutes, password-reset requests 3 per hour, join requests 3 per day per association, registrations 3 per day; administrators' activation-link resends (per association, per member, per address).
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

    /**
     * Association registrations: 3 per day per founder's email (go-live item of #35, issue #40). Every registration sends the founder an
     * activation email, so without it one address could be flooded from many IPs. Counted for every attempt whose input is valid, whatever
     * becomes of it (a taken short name included), so the limit says nothing about the address.
     *
     * @throws com.regivolley.api.application.exception.RateLimitExceededException if this email registered too often
     */
    void checkRegistration(String founderEmail);

    /**
     * Administrator re-sending a member's activation link: 30 per hour for the whole association (so one tenant cannot fill the
     * resend lane or flood mailboxes) and 3 per hour for the member. Taken after the administrator is authorised and before the
     * member is looked up, so it is counted the same for every outcome, an id of another association included.
     *
     * @throws com.regivolley.api.application.exception.RateLimitExceededException if the association or this member was asked for too often
     */
    void checkActivationLinkResend(AssociationId associationId, MemberId memberId);

    /**
     * A link about to be mailed to this address on an administrator's request: 6 per hour per address, across associations, so one
     * mailbox cannot be bombed by administrators of several associations that all list it. Deliberately its own bucket: sharing it with
     * password-reset requests would let anyone who can trigger resends lock the owner out of their own resets.
     *
     * @throws com.regivolley.api.application.exception.RateLimitExceededException if this address was mailed too often
     */
    void checkLinkMailAddress(EmailAddress address);
}
