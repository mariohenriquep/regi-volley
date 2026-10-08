package com.regivolley.api.application.command;

import java.util.Objects;

/** A visitor asks to join the association with that public short name (US-05). No actor: the tenant is resolved from the short name, the one public entry point (architecture.md section 8). */
public record SubmitJoinRequestCommand(String shortName, String name, String email, String phone, boolean consentAccepted, String policyVersion) {

    public SubmitJoinRequestCommand {
        Objects.requireNonNull(shortName, "shortName must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
    }
}
