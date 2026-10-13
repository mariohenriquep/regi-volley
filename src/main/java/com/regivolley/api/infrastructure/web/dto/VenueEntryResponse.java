package com.regivolley.api.infrastructure.web.dto;

import java.util.UUID;

/** A venue in the administrator's list; {@code inUseByActiveGroup} tells the screen that deleting it will be refused (still decided on submit). */
public record VenueEntryResponse(UUID id, String name, String address, int courts, boolean inUseByActiveGroup) {
}
