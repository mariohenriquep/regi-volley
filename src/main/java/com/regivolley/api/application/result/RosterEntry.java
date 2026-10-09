package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;

/**
 * One live booking of a session as the coach sees it: the booking id attendance is marked with, the member and the name to show for
 * them. Deliberately nothing else about the person (no email, no phone).
 */
public record RosterEntry(BookingId bookingId, MemberId memberId, String memberName, BookingStatus status) {

    public RosterEntry {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(memberName, "memberName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
