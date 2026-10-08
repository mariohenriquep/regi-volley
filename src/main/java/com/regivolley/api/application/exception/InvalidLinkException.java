package com.regivolley.api.application.exception;

/** The emailed link is unknown, expired, already used or superseded. Says nothing about which (threat model D-11). */
public class InvalidLinkException extends RuntimeException {

    public InvalidLinkException() {
        super("Invalid or expired link");
    }
}
