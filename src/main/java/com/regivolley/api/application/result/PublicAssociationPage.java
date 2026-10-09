package com.regivolley.api.application.result;

import java.util.List;
import java.util.Objects;

/**
 * What a visitor may see of an association (US-24, threat model P4): an explicit whitelist - name, locality, the contact address the
 * association entered, levels, active groups with their schedule and venue, venues. No ids, no member data, no counts.
 */
public record PublicAssociationPage(String name, String shortName, String locality, String contactEmail,
                                    List<PublicLevel> levels, List<PublicGroup> groups, List<PublicVenue> venues) {

    public PublicAssociationPage {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(shortName, "shortName must not be null");
        Objects.requireNonNull(locality, "locality must not be null");
        Objects.requireNonNull(contactEmail, "contactEmail must not be null");
        levels = List.copyOf(levels);
        groups = List.copyOf(groups);
        venues = List.copyOf(venues);
    }
}
