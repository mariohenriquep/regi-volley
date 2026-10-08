package com.regivolley.api.infrastructure.web.dto;

import java.util.UUID;

/** Who the caller is, as the server resolved them: the ids of their member and association. No roles, no personal data. */
public record MeResponse(UUID memberId, UUID associationId) {
}
