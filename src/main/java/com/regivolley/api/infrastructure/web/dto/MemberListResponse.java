package com.regivolley.api.infrastructure.web.dto;

import java.util.List;

/** The administrator's member list, by name; {@code truncated} when more members matched than the list holds (narrow it with {@code q} or {@code status}). */
public record MemberListResponse(List<MemberListEntryResponse> members, boolean truncated) {

    @Override
    public String toString() {
        return "MemberListResponse[redacted]";
    }
}
