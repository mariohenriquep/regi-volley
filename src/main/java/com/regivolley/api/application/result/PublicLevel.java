package com.regivolley.api.application.result;

import java.util.Objects;

/** A level as the public page shows it: its name only (the page lists them lowest first). */
public record PublicLevel(String name) {

    public PublicLevel {
        Objects.requireNonNull(name, "name must not be null");
    }
}
