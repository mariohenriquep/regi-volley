package com.regivolley.api.infrastructure.web.dto;

import java.util.UUID;

public record VenueResponse(UUID id, String name, String address, int courts) {
}
