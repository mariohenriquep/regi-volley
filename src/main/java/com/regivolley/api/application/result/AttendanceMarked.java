package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.List;

/**
 * Outcome of marking attendance (US-17): the stored session and the members who just reached the
 * monthly no-show limit and were warned (RN-11: a warning, never a block).
 */
public record AttendanceMarked(Session session, List<MemberId> warnedMembers) {

    public AttendanceMarked {
        warnedMembers = List.copyOf(warnedMembers);
    }
}
