package com.regivolley.api.application.identity;

import com.regivolley.api.application.identity.AccountLinkPurpose;

import java.time.Duration;

/** How long an emailed link lives (threat model D-11): activation outlasts a weekend, a reset is short. */
public final class EmailLinkPolicy {

    /** Administrators approve at night and members act days later. */
    public static final Duration ACTIVATION_LIFETIME = Duration.ofDays(7);
    public static final Duration RESET_LIFETIME = Duration.ofMinutes(30);

    private EmailLinkPolicy() {
    }

    public static Duration lifetimeOf(AccountLinkPurpose purpose) {
        return purpose == AccountLinkPurpose.ACTIVATION ? ACTIVATION_LIFETIME : RESET_LIFETIME;
    }
}
