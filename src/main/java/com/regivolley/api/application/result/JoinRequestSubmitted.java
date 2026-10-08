package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.JoinRequestId;

import java.util.Objects;

/** Outcome of submitting a join request (US-05): only its id, so the visitor gets nothing back about the association's members. */
public record JoinRequestSubmitted(JoinRequestId requestId) {

    public JoinRequestSubmitted {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
