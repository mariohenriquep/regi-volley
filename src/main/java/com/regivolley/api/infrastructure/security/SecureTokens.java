package com.regivolley.api.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random secrets and their hashes. A refresh token, an emailed link token and a security stamp are each 256 random bits from
 * {@link SecureRandom}, base64url without padding (43 characters). Stored secrets are SHA-256 hashes (hex): the values are
 * high-entropy, so a slow hash would add nothing (threat model D-7).
 */
final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    private SecureTokens() {
    }

    /** A fresh 256-bit secret, base64url. */
    static String newSecret() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the value as lower-case hex (64 characters): what the database stores in place of a token. */
    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
