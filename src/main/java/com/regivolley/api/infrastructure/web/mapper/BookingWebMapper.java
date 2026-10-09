package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.BookSessionCommand;
import com.regivolley.api.application.command.CancelBookingCommand;
import com.regivolley.api.application.command.ListBookableSessionsQuery;
import com.regivolley.api.application.command.MemberHistoryQuery;
import com.regivolley.api.application.result.BookableSession;
import com.regivolley.api.application.result.CancelledBooking;
import com.regivolley.api.application.result.HistoryEntry;
import com.regivolley.api.application.result.MemberHistory;
import com.regivolley.api.application.result.PlacedBooking;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.infrastructure.web.dto.BookableSessionResponse;
import com.regivolley.api.infrastructure.web.dto.BookingCancelledResponse;
import com.regivolley.api.infrastructure.web.dto.BookingPlacedResponse;
import com.regivolley.api.infrastructure.web.dto.MemberHistoryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a member does with bookings (US-13 to US-16, US-18): always for the caller, never for someone else. */
public final class BookingWebMapper {

    private BookingWebMapper() {
    }

    public static BookSessionCommand bookCommand(Actor actor, UUID sessionId) {
        return new BookSessionCommand(actor, SessionId.of(sessionId));
    }

    public static CancelBookingCommand cancelCommand(Actor actor, UUID sessionId, UUID bookingId) {
        return new CancelBookingCommand(actor, SessionId.of(sessionId), BookingId.of(bookingId));
    }

    /** The week that contains {@code weekOf}; without it, the current one. */
    public static ListBookableSessionsQuery weekQuery(Actor actor, LocalDate weekOf) {
        return new ListBookableSessionsQuery(actor, weekOf);
    }

    public static MemberHistoryQuery historyQuery(Actor actor) {
        return new MemberHistoryQuery(actor);
    }

    public static BookingPlacedResponse toResponse(PlacedBooking placed) {
        return new BookingPlacedResponse(placed.session().id().value(), placed.booking().id().value(), placed.booking().status().name(),
                placed.waitlistPosition().isPresent() ? placed.waitlistPosition().getAsInt() : null);
    }

    public static BookingCancelledResponse toResponse(CancelledBooking cancelled) {
        return new BookingCancelledResponse(cancelled.session().id().value(), cancelled.cancelled().id().value(), cancelled.late(),
                cancelled.creditRefunded(), cancelled.promoted().size());
    }

    public static List<BookableSessionResponse> toResponses(List<BookableSession> sessions) {
        return sessions.stream().map(BookingWebMapper::toResponse).toList();
    }

    private static BookableSessionResponse toResponse(BookableSession session) {
        return new BookableSessionResponse(session.sessionId().value(), session.trainingGroupId().value(), session.groupName(),
                session.startsAt(), session.endsAt(), session.bookingOpensAt(), session.capacity(), session.freeSeats(),
                session.waitlistSize(), session.myBookingStatus().map(BookingStatus::name).orElse(null));
    }

    public static MemberHistoryResponse toResponse(MemberHistory history) {
        return new MemberHistoryResponse(history.entries().stream().map(BookingWebMapper::toResponse).toList(),
                history.noShowsThisMonth(), history.noShowLimit(), history.nearLimit());
    }

    private static MemberHistoryResponse.EntryView toResponse(HistoryEntry entry) {
        return new MemberHistoryResponse.EntryView(entry.sessionId().value(), entry.trainingGroupId().value(), entry.startsAt(),
                entry.endsAt(), entry.bookingId().value(), entry.status().name());
    }
}
