package com.regivolley.api.application.command;

import java.util.List;
import java.util.Objects;

/** A visitor registers an association and becomes its administrator (US-01). No actor: the visitor has no account yet. {@code nif} is optional; the founder's contact data and the RGPD consent are required. */
public record RegisterAssociationCommand(String name, String shortName, String nif, String locality, String contactEmail, List<String> levelNames, String founderName, String founderEmail, String founderPhone, boolean consentAccepted, String policyVersion) {

    public RegisterAssociationCommand {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(shortName, "shortName must not be null");
        Objects.requireNonNull(locality, "locality must not be null");
        Objects.requireNonNull(contactEmail, "contactEmail must not be null");
        Objects.requireNonNull(levelNames, "levelNames must not be null");
        Objects.requireNonNull(founderName, "founderName must not be null");
        Objects.requireNonNull(founderEmail, "founderEmail must not be null");
        Objects.requireNonNull(founderPhone, "founderPhone must not be null");
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
    }
}
