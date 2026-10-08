package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;

/** One past booking of the member (US-18). */
public record HistoryEntry(SessionId sessionId, TrainingGroupId trainingGroupId, Instant startsAt, Instant endsAt,
                           BookingId bookingId, BookingStatus status) {
}
