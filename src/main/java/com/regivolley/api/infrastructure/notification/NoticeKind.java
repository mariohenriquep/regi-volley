package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.application.identity.AccountLinkPurpose;

/**
 * The emails the application sends, each with its template ({@code mail/<template>.txt} and {@code .html}) and a fixed subject.
 * A subject never carries a value: an association or member name, written by a visitor, stays out of the header lines. The two
 * account-link notices also name the frontend route that handles their link - the one place where a link purpose is mapped to its
 * notice and its route.
 */
enum NoticeKind {
    ACTIVATION("activation", "Activate your RegiVolley account", "/activate"),
    PASSWORD_RESET("password-reset", "Reset your RegiVolley password", "/reset-password"),
    BOOKING_PROMOTED("booking-promoted", "You were moved up from the waitlist", null),
    SESSION_CANCELLED("session-cancelled", "A session you signed up for was cancelled", null),
    NO_SHOW_MEMBER("no-show-member", "You reached the monthly no-show limit", null),
    NO_SHOW_ADMIN("no-show-admin", "A member reached the monthly no-show limit", null),
    JOIN_APPROVED("join-approved", "Your request to join was approved", null),
    JOIN_REJECTED("join-rejected", "Your request to join was not approved", null);

    private final String template;
    private final String subject;
    private final String path;

    NoticeKind(String template, String subject, String path) {
        this.template = template;
        this.subject = subject;
        this.path = path;
    }

    static NoticeKind of(AccountLinkPurpose purpose) {
        return switch (purpose) {
            case ACTIVATION -> ACTIVATION;
            case PASSWORD_RESET -> PASSWORD_RESET;
        };
    }

    String template() {
        return template;
    }

    String subject() {
        return subject;
    }

    /** The frontend route of an account-link notice, e.g. {@code /activate}. */
    String path() {
        if (path == null) {
            throw new IllegalStateException(name() + " has no link");
        }
        return path;
    }
}
