package com.regivolley.api.application.port;

/**
 * Outbound port for work that must not run on the request thread, so that response time says nothing (threat model D-10: a
 * password-reset request answers the same for an address with an account and one without). Bounded by the adapter: when it cannot
 * take the work it drops and logs it rather than running it on the caller.
 *
 * <p>Work is submitted to a {@link Lane}. Each lane has capacity of its own, so a flood in one cannot make another drop its work:
 * an administrator's resends can never take the room a visitor's password-reset mail needs.
 */
public interface BackgroundWork {

    /** Which capacity the work uses. */
    enum Lane {
        /** Mail a visitor asked for: password-reset requests. Reserved for them. */
        ACCOUNT_MAIL,
        /** Mail an administrator asked for: re-sent activation links. Small, and cannot starve {@link #ACCOUNT_MAIL}. */
        ADMIN_RESEND
    }

    void run(Lane lane, Runnable work);
}
