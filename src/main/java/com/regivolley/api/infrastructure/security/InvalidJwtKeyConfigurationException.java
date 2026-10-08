package com.regivolley.api.infrastructure.security;

/**
 * The JWT key configuration is unusable (missing, wrong curve, no kid, mismatch, duplicates, ...). Thrown at start-up so
 * the application refuses to boot (threat model D-2). The message names the problem, never the key material.
 */
public class InvalidJwtKeyConfigurationException extends IllegalStateException {

    public InvalidJwtKeyConfigurationException(String message) {
        super(message);
    }
}
