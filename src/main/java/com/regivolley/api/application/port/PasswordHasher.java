package com.regivolley.api.application.port;

/**
 * Outbound port for password hashing (threat model D-8). The adapter chooses the algorithm (Argon2id, with legacy formats still
 * verifiable) and bounds how many hashes run at once; the application only hashes, verifies and asks whether a stored hash should
 * be replaced. Hashing is the expensive operation of a login, so callers throttle before they call it.
 */
public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String storedHash);

    /** Whether the stored hash is in a legacy format (or weaker parameters) and should be replaced after a successful login. */
    boolean needsUpgrade(String storedHash);

    /** Spends the time of one verification against a dummy hash; the result is meaningless. Used when there is nothing to verify (D-10). */
    void burn(String password);
}
