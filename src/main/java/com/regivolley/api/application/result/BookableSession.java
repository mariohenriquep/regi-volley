package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One line of the week view (US-13): the session, where it is held, how many seats are free, how long the waitlist is,
 * until when cancelling a confirmed booking is free (RN-10) and - only for the caller - their own live booking: its status,
 * its id (so it can be cancelled) and, when waitlisted, their 1-based place in the queue. Nothing about anyone else's booking.
 */
public record BookableSession(SessionId sessionId, TrainingGroupId trainingGroupId, String groupName, Optional<String> venueName,
                              Instant startsAt, Instant endsAt, Instant bookingOpensAt, Instant freeCancellationUntil,
                              int capacity, int freeSeats, int waitlistSize, Optional<BookingStatus> myBookingStatus,
                              Optional<BookingId> myBookingId, OptionalInt myWaitlistPosition) {
}
