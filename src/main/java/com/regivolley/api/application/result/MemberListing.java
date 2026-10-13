package com.regivolley.api.application.result;

import java.util.List;
import java.util.Objects;

/** The administrator's member list, by name, with {@code truncated} set when more members matched than the list may hold. */
public record MemberListing(List<MemberListEntry> members, boolean truncated) {

    public MemberListing {
        Objects.requireNonNull(members, "members must not be null");
        members = List.copyOf(members);
    }
}
