package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.shared.FieldRules;
import com.regivolley.api.domain.shared.ValueObject;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Proof that a person explicitly accepted the RGPD privacy policy (US-05, NFR RGPD): when, and
 * which version of the policy. Stamped by the server clock, never taken from the client. Not
 * personal data in itself, so it survives anonymisation as the record of the lawful basis.
 */
public record GdprConsent(Instant givenAt, String policyVersion) implements ValueObject {

    public static final int MAX_POLICY_VERSION_LENGTH = 50;

    public GdprConsent {
        Objects.requireNonNull(givenAt, "givenAt must not be null");
        policyVersion = FieldRules.requiredText("policy version", policyVersion, MAX_POLICY_VERSION_LENGTH);
    }

    /**
     * Records an acceptance made now.
     *
     * @throws ConsentRequiredException if the person did not accept
     * @throws InvalidFieldException    if the policy version is blank
     */
    public static GdprConsent record(boolean accepted, String policyVersion, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return record(accepted, policyVersion, clock.instant());
    }

    /** Same as {@link #record(boolean, String, Clock)} for a caller that already read the clock. */
    public static GdprConsent record(boolean accepted, String policyVersion, Instant at) {
        if (!accepted) {
            throw new ConsentRequiredException();
        }
        return new GdprConsent(at, policyVersion);
    }
}
