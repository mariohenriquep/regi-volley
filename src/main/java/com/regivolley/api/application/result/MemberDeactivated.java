package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.SessionId;

import java.util.List;

/**
 * Outcome of deactivating a member (US-08): the stored, now INACTIVE member, how many future bookings were
 * cancelled and the sessions whose booking could not be cancelled this time (a persistent conflict or an
 * unexpected failure). The member stays INACTIVE either way; running the deactivation again retries just those.
 */
public record MemberDeactivated(Member member, int bookingsCancelled, List<SessionId> failedSessions) {

    public MemberDeactivated {
        failedSessions = List.copyOf(failedSessions);
    }
}
