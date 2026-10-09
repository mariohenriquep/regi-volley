package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token from the emailed reset link and the new password. A credential: never printed. */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(max = 1000) String password) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[redacted]";
    }
}
