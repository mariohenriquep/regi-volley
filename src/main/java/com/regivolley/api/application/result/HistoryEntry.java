package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;
import java.util.Optional;

/** One past booking of the member (US-18), with the name of its group and of the venue it was held at, when they can be found. */
public record HistoryEntry(SessionId sessionId, TrainingGroupId trainingGroupId, Optional<String> trainingGroupName,
                           Optional<String> venueName, Instant startsAt, Instant endsAt, BookingId bookingId,
                           BookingStatus status) {
}
