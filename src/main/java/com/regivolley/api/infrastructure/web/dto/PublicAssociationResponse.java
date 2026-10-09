package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The public page of an association (US-24, threat model P4): an explicit whitelist. No ids, no member or coach data, no counts.
 * The contact address is the one the association entered for the public.
 */
public record PublicAssociationResponse(String name, String shortName, String locality, String contactEmail, List<String> levels,
                                        List<GroupView> groups, List<VenueView> venues) {

    /** An active group: where and when it trains and which levels it welcomes. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupView(String name, List<String> levels, String venue, List<SlotView> schedule) {
    }

    /** One weekly slot in Lisbon local time. */
    public record SlotView(String dayOfWeek, String startTime, int durationMinutes) {
    }

    public record VenueView(String name, String address, int courts) {
    }
}
