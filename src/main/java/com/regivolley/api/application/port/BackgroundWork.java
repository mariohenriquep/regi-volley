package com.regivolley.api.application.port;

/**
 * Outbound port for work that must not run on the request thread, so that response time says nothing (threat model D-10: a
 * password-reset request answers the same for an address with an account and one without). Bounded by the adapter: when it cannot
 * take the work it drops and logs it rather than running it on the caller.
 */
public interface BackgroundWork {

    void run(Runnable work);
}
