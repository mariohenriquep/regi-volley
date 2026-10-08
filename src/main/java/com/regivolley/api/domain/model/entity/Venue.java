package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidCourtCountException;
import com.regivolley.api.domain.exception.InvalidVenueException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.shared.AggregateRoot;
import com.regivolley.api.domain.shared.FieldRules;

import java.util.Objects;

/**
 * A place where an association trains (Pavilhao, US-02): a name, an address and the number of courts
 * (at least one). Immutable: an edit returns a new instance. A training group points at it by
 * {@link VenueId}; the rule that a venue used by an active group cannot be deleted needs the groups, so it is
 * the delete use case's job (through a repository), not this aggregate's.
 */
public final class Venue implements AggregateRoot {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_ADDRESS_LENGTH = 200;

    private final VenueId id;
    private final AssociationId associationId;
    private final String name;
    private final String address;
    private final int courts;
    private final long version;

    private Venue(VenueId id, AssociationId associationId, String name, String address, int courts, long version) {
        this.id = id;
        this.associationId = associationId;
        this.name = name;
        this.address = address;
        this.courts = courts;
        this.version = version;
    }

    public static Venue create(AssociationId associationId, String name, String address, int courts) {
        return reconstruct(VenueId.generate(), associationId, name, address, courts, 0L);
    }

    /**
     * Rebuilds a venue from persisted data, re-checking its invariants.
     *
     * @param version the optimistic-lock version it was loaded with (0 for a new venue); edits carry it over
     *                unchanged, so a stale copy is detected when it is saved (architecture.md section 10)
     */
    public static Venue reconstruct(VenueId id, AssociationId associationId, String name, String address,
                                    int courts, long version) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        if (version < 0) {
            throw new InvalidVenueException("The version must not be negative");
        }
        if (courts < 1) {
            throw new InvalidCourtCountException(courts);
        }
        return new Venue(id, associationId, FieldRules.requiredText("venue name", name, MAX_NAME_LENGTH),
                FieldRules.requiredText("venue address", address, MAX_ADDRESS_LENGTH), courts, version);
    }

    public Venue edit(String newName, String newAddress, int newCourts) {
        return reconstruct(id, associationId, newName, newAddress, newCourts, version);
    }

    public VenueId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public String name() {
        return name;
    }

    public String address() {
        return address;
    }

    public int courts() {
        return courts;
    }

    /** Optimistic-lock version: concurrent edits, or an edit racing a delete, must not be lost. */
    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Venue other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Venue{id=%s, associationId=%s}".formatted(id, associationId);
    }
}
