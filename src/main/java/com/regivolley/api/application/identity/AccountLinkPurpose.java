package com.regivolley.api.application.identity;

/** What an emailed single-use link is for (threat model D-11). */
public enum AccountLinkPurpose {
    /** First sign-in: proves the mailbox, confirms the membership and sets the first password. */
    ACTIVATION,
    /** Forgotten password: proves the mailbox and sets a new password. */
    PASSWORD_RESET
}
