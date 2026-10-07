package com.regivolley.api.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The personal data a person gives to join an association (US-05): name, email and phone, and
 * nothing else (RGPD minimisation). Shared by {@link JoinRequest} and {@link Member}, so both
 * validate, erase and hide it the same way. Immutable. Never printed: {@link #toString()} hides
 * everything (architecture.md section 11).
 *
 * <p>The phone is collected, hence required when the details are built with {@link #of}, but it
 * is absent once the details are erased ({@link #anonymisedFor}), which makes an erased record
 * distinguishable from a live one.
 */
public final class ContactDetails {

    public static final int MAX_NAME_LENGTH = 100;
    static final String ANONYMISED_NAME = "Anonymised member";

    private final String name;
    private final EmailAddress email;
    private final PhoneNumber phone;

    private ContactDetails(String name, EmailAddress email, PhoneNumber phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    /** Details collected from a person: name, email and phone are all required. */
    public static ContactDetails of(String name, EmailAddress email, PhoneNumber phone) {
        Objects.requireNonNull(phone, "phone must not be null");
        return reconstruct(name, email, phone);
    }

    /** Rebuilds details from persisted data; {@code phone} is null for an erased record. */
    public static ContactDetails reconstruct(String name, EmailAddress email, PhoneNumber phone) {
        return new ContactDetails(
                FieldRules.requiredText("name", name, MAX_NAME_LENGTH),
                Objects.requireNonNull(email, "email must not be null"),
                phone
        );
    }

    /**
     * Placeholders for erased details: a fixed name, an email unique to {@code recordId} (so
     * records stay distinguishable and a unique email constraint still holds) and no phone.
     */
    static ContactDetails anonymisedFor(UUID recordId) {
        return new ContactDetails(ANONYMISED_NAME, EmailAddress.anonymisedFor(recordId), null);
    }

    public String name() {
        return name;
    }

    public EmailAddress email() {
        return email;
    }

    /** Empty only on erased details. */
    public Optional<PhoneNumber> phone() {
        return Optional.ofNullable(phone);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContactDetails other)) return false;
        return name.equals(other.name) && email.equals(other.email) && Objects.equals(phone, other.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, phone);
    }

    @Override
    public String toString() {
        return "ContactDetails[redacted]";
    }
}
