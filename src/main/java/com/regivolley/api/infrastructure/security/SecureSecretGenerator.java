package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.SecretGenerator;

/** {@link SecretGenerator} on {@link SecureTokens}: 256 random bits from {@code SecureRandom}, stored as SHA-256 hex. */
public class SecureSecretGenerator implements SecretGenerator {

    @Override
    public String newSecret() {
        return SecureTokens.newSecret();
    }

    @Override
    public String hash(String secret) {
        return SecureTokens.sha256(secret);
    }
}
