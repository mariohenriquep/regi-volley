package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.MemberId;

/**
 * A member was changed by a concurrent request between load and save, so this write was rejected and
 * nothing was stored. Among other things this guarantees that a stale copy can never write the old
 * name, email and phone back over an RGPD erasure.
 */
public class MemberModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final MemberId memberId;

    public MemberModifiedConcurrentlyException(MemberId memberId) {
        super("member", memberId.value());
        this.memberId = memberId;
    }

    public MemberId memberId() {
        return memberId;
    }
}
