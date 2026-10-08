package com.regivolley.api.application.command;

import java.util.Objects;

/** An administrator adds a venue (US-02). */
public record CreateVenueCommand(Actor actor, String name, String address, int courts) {

    public CreateVenueCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(address, "address must not be null");
    }
}
