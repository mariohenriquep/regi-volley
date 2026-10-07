package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;
import java.util.Optional;

/**
 * One line of the week view (US-13): the session, how many seats are free, how long the waitlist is
 * and the member's own booking status in it, if they have a live one.
 */
public record BookableSession(SessionId sessionId, TrainingGroupId trainingGroupId, String groupName,
                              Instant startsAt, Instant endsAt, Instant bookingOpensAt, int capacity, int freeSeats,
                              int waitlistSize, Optional<BookingStatus> myBookingStatus) {
}
