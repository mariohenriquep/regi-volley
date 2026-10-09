package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

/** The optional reason an administrator gives when rejecting a join request (US-06). */
public record RejectionRequest(@Size(max = 500) String reason) {
}
