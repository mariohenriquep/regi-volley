package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token from the emailed activation link and the first password. A credential: never printed. */
public record ActivateAccountRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(max = 1000) String password) {

    @Override
    public String toString() {
        return "ActivateAccountRequest[redacted]";
    }
}
