package com.regivolley.api.application.port;

/** Outbound port for the offline list of common passwords (threat model D-8); a breached-password API is deferred. */
public interface CommonPasswordList {

    /** Whether the password is on the list, compared case-insensitively after Unicode normalisation. */
    boolean contains(String password);
}
