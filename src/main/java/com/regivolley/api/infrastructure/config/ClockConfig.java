package com.regivolley.api.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Exposes the system clock (UTC) as a bean so application-layer classes depend on an
 * injectable {@link Clock} instead of calling {@code Instant.now()} directly - this is what
 * lets booking deadlines, cancellation windows and session generation be tested
 * deterministically with a fixed clock.
 *
 * <p>The clock ticks in whole microseconds, the precision of PostgreSQL's {@code timestamptz}: an
 * instant read from it then survives a database round trip unchanged, so a booking's
 * {@code requestedAt} compares equal before and after saving.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
