package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Member;

import java.util.Objects;
import java.util.Optional;

/**
 * One line of the administrator's member list: the member (personal data, for the administrator only), the name of their level (empty
 * if the level cannot be found) and whether they have activated their account ({@code accountActivated}: the membership is
 * CONFIRMED rather than PENDING, or missing).
 */
public record MemberListEntry(Member member, Optional<String> levelName, boolean accountActivated) {

    public MemberListEntry {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(levelName, "levelName must not be null");
    }
}
