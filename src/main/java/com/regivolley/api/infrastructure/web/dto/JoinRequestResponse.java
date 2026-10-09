package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/** A join request as an administrator sees it. Personal data: {@link #toString()} prints nothing. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JoinRequestResponse(UUID id, String name, String email, String phone, String status, Instant requestedAt,
                                  Instant decidedAt, String rejectionReason, String policyVersion) {

    @Override
    public String toString() {
        return "JoinRequestResponse[redacted]";
    }
}
