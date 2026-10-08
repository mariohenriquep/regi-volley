package com.regivolley.api.application.port;

/**
 * Outbound port for secrets: 256 random bits for refresh tokens, link tokens and security stamps, and the one-way hash under
 * which a token is stored (threat model D-7, D-11). Tokens are high-entropy, so the hash is a fast one; a slow password hash adds nothing.
 */
public interface SecretGenerator {

    /** A fresh, unguessable, URL-safe secret. */
    String newSecret();

    /** The stored form of a token: a one-way hash that the database can index and compare. */
    String hash(String secret);
}
