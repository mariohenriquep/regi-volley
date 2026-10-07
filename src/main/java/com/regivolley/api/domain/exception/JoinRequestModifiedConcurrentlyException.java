package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.JoinRequestId;

/**
 * A join request was changed by a concurrent request between load and save, so this write was rejected
 * and nothing was stored: an approval and a rejection made at once cannot both succeed.
 */
public class JoinRequestModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final JoinRequestId joinRequestId;

    public JoinRequestModifiedConcurrentlyException(JoinRequestId joinRequestId) {
        super("join request", joinRequestId.value());
        this.joinRequestId = joinRequestId;
    }

    public JoinRequestId joinRequestId() {
        return joinRequestId;
    }
}
