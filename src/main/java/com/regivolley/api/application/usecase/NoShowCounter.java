package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.LisbonMonth;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.SessionRepository;

import java.time.Instant;

/** How many no-shows a member has in the Lisbon calendar month of a given instant (RN-11), counted by session date. */
final class NoShowCounter {

    private final SessionRepository sessions;

    NoShowCounter(SessionRepository sessions) {
        this.sessions = sessions;
    }

    int inMonthOf(AssociationId associationId, MemberId memberId, Instant instant) {
        LisbonMonth month = LisbonMonth.containing(instant);
        return (int) sessions.findWithLiveBookingOverlapping(associationId, memberId, month.start(), month.endExclusive())
                .stream()
                .filter(session -> month.contains(session.startsAt()))
                .flatMap(session -> session.bookings().stream())
                .filter(booking -> booking.memberId().equals(memberId) && booking.status() == BookingStatus.NO_SHOW)
                .count();
    }
}
