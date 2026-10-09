package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A member as an administrator sees them: contact data, status, level and roles. Personal data: {@link #toString()} prints nothing. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberResponse(UUID id, String name, String email, String phone, String status, UUID levelId, List<String> roles,
                             Instant joinedAt) {

    @Override
    public String toString() {
        return "MemberResponse[redacted]";
    }
}
