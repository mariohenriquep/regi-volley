package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

/** Why the session in the path is cancelled; the members are told (US-12). The domain requires a reason. */
public record CancelSessionRequest(@Size(max = 500) String reason) {
}
