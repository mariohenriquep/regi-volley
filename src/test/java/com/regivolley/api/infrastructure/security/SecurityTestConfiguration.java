package com.regivolley.api.infrastructure.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/** The collaborators the web security slice needs: a fixed clock and an in-memory account lookup standing in for the database one. */
@TestConfiguration
public class SecurityTestConfiguration {

    @Bean
    @Primary
    public Clock fixedClock() {
        return SecurityFixtures.CLOCK;
    }

    @Bean
    public InMemorySecurityAccountLookup accountLookup() {
        return new InMemorySecurityAccountLookup();
    }
}
