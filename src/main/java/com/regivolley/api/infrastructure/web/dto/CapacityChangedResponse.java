package com.regivolley.api.infrastructure.web.dto;

/** The session with its new capacity and how many people moved up from the waitlist because of it. */
public record CapacityChangedResponse(SessionResponse session, int promotedCount) {
}
