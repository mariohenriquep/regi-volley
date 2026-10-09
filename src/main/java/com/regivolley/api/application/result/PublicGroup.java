package com.regivolley.api.application.result;

import java.util.List;
import java.util.Objects;

/** An active training group as the public page shows it. The coach is deliberately not named: a person's data is not public. */
public record PublicGroup(String name, List<String> levelNames, String venueName, List<PublicSlot> schedule) {

    public PublicGroup {
        Objects.requireNonNull(name, "name must not be null");
        levelNames = List.copyOf(levelNames);
        schedule = List.copyOf(schedule);
    }
}
