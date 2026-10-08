package com.regivolley.api.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;

/**
 * Wires the access-token keys, the verifier and the issuer (threat model D-1, D-2). The profile decides one thing:
 * the ephemeral key pair is allowed only under {@code dev} or {@code test}, and never when {@code prod} is active too;
 * everywhere else (including no profile at all) a missing key refuses to start. Invalid keys refuse to start under every profile.
 */
@Configuration
@EnableConfigurationProperties(JwtKeyProperties.class)
public class JwtKeyConfiguration {

    @Bean
    public JwtKeySet jwtKeySet(JwtKeyProperties properties, Environment environment) {
        // prod wins over dev: "prod,dev" must not mint a throwaway key in production.
        boolean ephemeralAllowed = environment.acceptsProfiles(Profiles.of("dev | test")) && !environment.acceptsProfiles(Profiles.of("prod"));
        return JwtKeyLoader.load(properties.signingKey(), properties.verificationKeys(), ephemeralAllowed);
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtKeySet keys, Clock clock) {
        return AccessTokenDecoderFactory.create(keys, clock);
    }

    @Bean
    public AccessTokenIssuer accessTokenIssuer(JwtKeySet keys, Clock clock) {
        return new AccessTokenIssuer(keys, clock);
    }
}
