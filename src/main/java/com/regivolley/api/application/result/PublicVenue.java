package com.regivolley.api.application.result;

import java.util.Objects;

/** A venue as the public page shows it. */
public record PublicVenue(String name, String address, int courts) {

    public PublicVenue {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(address, "address must not be null");
    }
}
