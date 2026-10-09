package com.regivolley.api.infrastructure.security;

import java.time.Duration;

/**
 * The rate limits of the threat model (D-9): how many calls a key may make per window. IP rules are generous on purpose
 * (mobile carriers put many users behind one address); email rules are tight because they guard one account. There is no hard
 * lock-out: a bucket simply refills, so an attacker cannot lock a victim out for good.
 */
public enum RateLimitRule {

    LOGIN_EMAIL(5, Duration.ofMinutes(15), Keys.EMAIL),
    LOGIN_IP(30, Duration.ofMinutes(10), Keys.IP),
    RESET_EMAIL(3, Duration.ofHours(1), Keys.EMAIL),
    RESET_IP(10, Duration.ofHours(1), Keys.IP),
    REGISTER_IP(3, Duration.ofHours(1), Keys.IP),
    JOIN_IP(5, Duration.ofHours(1), Keys.IP),
    /** Keyed by association and email together ({@link RateLimiter#joinKey}). */
    JOIN_EMAIL(3, Duration.ofDays(1), Keys.EMAIL),
    /** An administrator re-sending one member's activation link, keyed by association and member ({@link RateLimiter#memberKey}). */
    ACTIVATION_RESEND(3, Duration.ofHours(1), Keys.MEMBER),
    /** All of one association's activation-link resends together, keyed by the association id (taken only for an authenticated administrator). */
    ASSOCIATION_RESEND(30, Duration.ofHours(1), Keys.TENANT),
    /** Link mails an administrator triggered to one address, across associations ({@link RateLimiter#emailKey}). */
    LINK_MAIL_PER_ADDRESS(6, Duration.ofHours(1), Keys.EMAIL),
    REFRESH_IP(60, Duration.ofMinutes(1), Keys.IP),
    PUBLIC_PAGE_IP(120, Duration.ofMinutes(1), Keys.IP),
    /** Activation and reset-confirmation: generous, since the tokens are 256-bit secrets; it only bounds hashing and lookups per address. */
    LINK_TOKEN_IP(30, Duration.ofHours(1), Keys.IP),
    /** Every authenticated route together (U7): a valid user hammering booking or history. Keyed by the user id, taken from the verified token. */
    USER(300, Duration.ofMinutes(1), Keys.USER);

    private final int capacity;
    private final Duration window;
    private final long maxKeys;

    RateLimitRule(int capacity, Duration window, long maxKeys) {
        this.capacity = capacity;
        this.window = window;
        this.maxKeys = maxKeys;
    }

    /** How many distinct keys the rule remembers at once; the oldest idle ones go first. Email-keyed rules are attacker-controlled, so they stay small. */
    public long maxKeys() {
        return maxKeys;
    }

    /** Key-cache sizes, apart because enum constants cannot see the enum's own statics. */
    private static final class Keys {
        static final long EMAIL = 20_000;
        static final long IP = 100_000;
        static final long USER = 50_000;
        static final long MEMBER = 20_000;
        static final long TENANT = 20_000;
    }

    public int capacity() {
        return capacity;
    }

    public Duration window() {
        return window;
    }
}
