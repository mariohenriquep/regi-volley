package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Send me a reset link." Only the shape is checked here, not whether the address looks valid or exists: the answer is the same
 * 202 either way (threat model D-10). Personal data: never printed.
 */
public record ForgotPasswordRequest(@NotBlank @Size(max = 254) String email) {

    @Override
    public String toString() {
        return "ForgotPasswordRequest[redacted]";
    }
}
