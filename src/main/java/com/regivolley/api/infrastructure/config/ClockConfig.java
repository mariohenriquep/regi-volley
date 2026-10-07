package com.regivolley.api.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system clock (UTC) as a bean so application-layer classes depend on an
 * injectable {@link Clock} instead of calling {@code Instant.now()} directly - this is what
 * lets booking deadlines, cancellation windows and session generation be tested
 * deterministically with a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
