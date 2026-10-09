package com.regivolley.api.application.exception;

/** The refresh token is unknown, expired, revoked, already rotated, or its member may no longer sign in. Says nothing about which. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }
}
