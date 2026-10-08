package com.regivolley.api.application.identity;

import java.time.Duration;

/**
 * The fixed terms of the refresh token (threat model D-7): how long a login lives idle and in total, and the grace for
 * two tabs presenting the same token. (The cookie that carries it is a web concern.)
 */
public final class RefreshTokenPolicy {

    /** A refresh token unused for this long is dead. */
    public static final Duration IDLE_LIFETIME = Duration.ofDays(30);
    /** A login ends this long after it started, however often it was refreshed. */
    public static final Duration ABSOLUTE_LIFETIME = Duration.ofDays(90);
    /** A just-rotated token presented again within this time is a parallel tab, not theft: refused, family kept. */
    public static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private RefreshTokenPolicy() {
    }
}
