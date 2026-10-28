package com.regivolley.api.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Who the caller is, as the server resolved them: the ids of their member and association and the roles they hold now
 * ({@code MEMBER}, {@code COACH}, {@code ADMIN}, in that order). The roles are information for the screens: the API authorises every
 * operation again. No personal data.
 */
public record MeResponse(UUID memberId, UUID associationId, List<MemberRoleName> roles) {
}
