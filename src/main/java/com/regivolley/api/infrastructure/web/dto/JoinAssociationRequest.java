package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A visitor asks to join the association named in the path (US-05). Personal data: {@link #toString()} prints nothing. */
public record JoinAssociationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String phone,
        @NotNull Boolean consentAccepted,
        @NotBlank @Size(max = 50) String policyVersion) {

    @Override
    public String toString() {
        return "JoinAssociationRequest[redacted]";
    }
}
