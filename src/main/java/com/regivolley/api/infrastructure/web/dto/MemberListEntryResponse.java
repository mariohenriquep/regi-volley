package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One member in the administrator's list. {@code accountActivated} is whether the member has used their activation link (their
 * membership is confirmed); {@code roles} are listed {@code MEMBER}, {@code COACH}, {@code ADMIN} in that order. Personal data:
 * {@link #toString()} prints nothing. {@code levelName} is left out when the level cannot be found.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberListEntryResponse(UUID memberId, String name, String email, String phone, UUID levelId, String levelName,
                                      List<MemberRoleName> roles, String status, boolean accountActivated, Instant joinedAt) {

    @Override
    public String toString() {
        return "MemberListEntryResponse[redacted]";
    }
}
