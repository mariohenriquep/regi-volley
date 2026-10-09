package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A visitor registers an association and becomes its first administrator (US-01). No role, tenant or id fields: the founder's
 * roles are fixed by the use case. The founder's contact data is personal, so {@link #toString()} prints nothing.
 */
public record RegisterAssociationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 40) String shortName,
        @Size(max = 20) String nif,
        @NotBlank @Size(max = 100) String locality,
        @NotBlank @Size(max = 254) String contactEmail,
        @NotNull @Size(min = 1, max = 20) List<@NotNull @Size(max = 50) String> levelNames,
        @NotBlank @Size(max = 100) String founderName,
        @NotBlank @Size(max = 254) String founderEmail,
        @NotBlank @Size(max = 30) String founderPhone,
        @NotNull Boolean consentAccepted,
        @NotBlank @Size(max = 50) String policyVersion) {

    @Override
    public String toString() {
        return "RegisterAssociationRequest[redacted]";
    }
}
