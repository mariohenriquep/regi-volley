package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.MemberRole;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The roles a member holds now, without repeats and in the order of {@link MemberRole}, so the answer is stable. */
public record MyRoles(List<MemberRole> roles) {

    public MyRoles {
        Objects.requireNonNull(roles, "roles must not be null");
        roles = roles.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }
}
