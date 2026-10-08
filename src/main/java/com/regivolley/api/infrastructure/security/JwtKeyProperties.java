package com.regivolley.api.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code JWT_SIGNING_KEY} and {@code JWT_VERIFICATION_KEYS} as bound from {@code application.yml}
 * ({@code regi-volley.security.jwt.*}). Raw text: parsing and validation are {@link JwtKeyLoader}'s. {@link #toString()}
 * redacts both values, because the first is a private key.
 */
@ConfigurationProperties("regi-volley.security.jwt")
public record JwtKeyProperties(@DefaultValue("") String signingKey, @DefaultValue("") String verificationKeys) {

    @Override
    public String toString() {
        return "JwtKeyProperties{signingKey=<redacted>, verificationKeys=<redacted>}";
    }
}
