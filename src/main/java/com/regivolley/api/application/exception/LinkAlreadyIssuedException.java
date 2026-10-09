package com.regivolley.api.application.exception;

/**
 * Two requests issued a link for the same membership (activation) or user (reset) at the same moment and the database let only one
 * stay open (a partial unique index allows one live link per membership and purpose, and one live reset per user). The loser
 * repeats its transaction; the newest link wins, as it should. Carries no data.
 */
public class LinkAlreadyIssuedException extends RuntimeException {

    public LinkAlreadyIssuedException() {
        super("Another link was issued at the same time");
    }
}
