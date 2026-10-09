package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.AttendanceEntry;
import com.regivolley.api.application.command.AttendanceMark;
import com.regivolley.api.application.command.CancelSessionCommand;
import com.regivolley.api.application.command.ChangeSessionCapacityCommand;
import com.regivolley.api.application.command.MarkAttendanceCommand;
import com.regivolley.api.application.result.AttendanceMarked;
import com.regivolley.api.application.result.CancelledSession;
import com.regivolley.api.application.result.CapacityChanged;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.infrastructure.web.dto.AttendanceMarkName;
import com.regivolley.api.infrastructure.web.dto.AttendanceMarkedResponse;
import com.regivolley.api.infrastructure.web.dto.CancelSessionRequest;
import com.regivolley.api.infrastructure.web.dto.CapacityChangedResponse;
import com.regivolley.api.infrastructure.web.dto.ChangeCapacityRequest;
import com.regivolley.api.infrastructure.web.dto.MarkAttendanceRequest;
import com.regivolley.api.infrastructure.web.dto.SessionCancelledResponse;
import com.regivolley.api.infrastructure.web.dto.SessionResponse;

import java.util.UUID;

/** What the staff of a session does to it (US-11, US-12, US-17) and the session as they see it, bookings included. */
public final class SessionWebMapper {

    private SessionWebMapper() {
    }

    public static CancelSessionCommand cancelCommand(Actor actor, UUID sessionId, CancelSessionRequest body) {
        return new CancelSessionCommand(actor, SessionId.of(sessionId), body.reason());
    }

    public static ChangeSessionCapacityCommand capacityCommand(Actor actor, UUID sessionId, ChangeCapacityRequest body) {
        return new ChangeSessionCapacityCommand(actor, SessionId.of(sessionId), body.capacity());
    }

    public static MarkAttendanceCommand attendanceCommand(Actor actor, UUID sessionId, MarkAttendanceRequest body) {
        return new MarkAttendanceCommand(actor, SessionId.of(sessionId), body.entries().stream()
                .map(entry -> new AttendanceEntry(BookingId.of(entry.bookingId()), toDomain(entry.mark()))).toList());
    }

    public static SessionResponse toResponse(Session session) {
        return new SessionResponse(session.id().value(), session.trainingGroupId().value(), session.coachId().value(), session.startsAt(),
                session.endsAt(), session.capacity(), session.status().name(), session.confirmedCount(), session.freeSeats(),
                session.waitlist().size(), session.cancellationReason().orElse(null),
                session.bookings().stream().map(SessionWebMapper::toResponse).toList());
    }

    private static SessionResponse.BookingView toResponse(Booking booking) {
        return new SessionResponse.BookingView(booking.id().value(), booking.memberId().value(), booking.status().name());
    }

    public static SessionCancelledResponse toResponse(CancelledSession cancelled) {
        return new SessionCancelledResponse(toResponse(cancelled.session()), cancelled.cancelledBookings().size(),
                cancelled.creditsRefunded());
    }

    public static CapacityChangedResponse toResponse(CapacityChanged changed) {
        return new CapacityChangedResponse(toResponse(changed.session()), changed.promoted().size());
    }

    public static AttendanceMarkedResponse toResponse(AttendanceMarked marked) {
        return new AttendanceMarkedResponse(toResponse(marked.session()), marked.warnedMembers().size());
    }

    /** Every wire spelling has its mark; a new value on either side stops the build here. */
    static AttendanceMark toDomain(AttendanceMarkName name) {
        return switch (name) {
            case ATTENDED -> AttendanceMark.ATTENDED;
            case NO_SHOW -> AttendanceMark.NO_SHOW;
        };
    }
}
