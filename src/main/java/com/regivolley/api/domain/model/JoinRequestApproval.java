package com.regivolley.api.domain.model;

import java.util.Objects;

/** Outcome of approving a join request (US-06): the request now APPROVED and the member it created. */
public record JoinRequestApproval(JoinRequest request, Member member) {

    public JoinRequestApproval {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(member, "member must not be null");
    }
}
