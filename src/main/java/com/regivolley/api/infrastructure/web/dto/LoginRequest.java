package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email and password to sign in. The password is never printed: {@link #toString()} shows neither field. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 1000) String password) {

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
