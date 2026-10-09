package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.BookSessionUseCase;
import com.regivolley.api.application.usecase.CancelBookingUseCase;
import com.regivolley.api.application.usecase.CancelSessionUseCase;
import com.regivolley.api.application.usecase.ChangeSessionCapacityUseCase;
import com.regivolley.api.application.usecase.GetSessionRosterUseCase;
import com.regivolley.api.application.usecase.ListBookableSessionsUseCase;
import com.regivolley.api.application.usecase.MarkAttendanceUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.AttendanceMarkedResponse;
import com.regivolley.api.infrastructure.web.dto.BookableSessionResponse;
import com.regivolley.api.infrastructure.web.dto.BookingCancelledResponse;
import com.regivolley.api.infrastructure.web.dto.BookingPlacedResponse;
import com.regivolley.api.infrastructure.web.dto.CancelSessionRequest;
import com.regivolley.api.infrastructure.web.dto.CapacityChangedResponse;
import com.regivolley.api.infrastructure.web.dto.ChangeCapacityRequest;
import com.regivolley.api.infrastructure.web.dto.MarkAttendanceRequest;
import com.regivolley.api.infrastructure.web.dto.PlausibleDate;
import com.regivolley.api.infrastructure.web.dto.SessionCancelledResponse;
import com.regivolley.api.infrastructure.web.dto.SessionRosterResponse;
import com.regivolley.api.infrastructure.web.mapper.BookingWebMapper;
import com.regivolley.api.infrastructure.web.mapper.SessionWebMapper;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sessions and their bookings. Members list the week and book or cancel their own place (US-13 to US-16); the staff of a session
 * (its coach or an administrator, decided by the use cases) cancel it, change its seats and mark attendance (US-11, US-12, US-17).
 * The session in the path is looked up inside the caller's association, so another association's session is a 404.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final ListBookableSessionsUseCase listBookableSessions;
    private final BookSessionUseCase bookSession;
    private final CancelBookingUseCase cancelBooking;
    private final CancelSessionUseCase cancelSession;
    private final ChangeSessionCapacityUseCase changeCapacity;
    private final MarkAttendanceUseCase markAttendance;
    private final GetSessionRosterUseCase getRoster;

    public SessionController(ListBookableSessionsUseCase listBookableSessions, BookSessionUseCase bookSession,
                             CancelBookingUseCase cancelBooking, CancelSessionUseCase cancelSession,
                             ChangeSessionCapacityUseCase changeCapacity, MarkAttendanceUseCase markAttendance,
                             GetSessionRosterUseCase getRoster) {
        this.listBookableSessions = listBookableSessions;
        this.bookSession = bookSession;
        this.cancelBooking = cancelBooking;
        this.cancelSession = cancelSession;
        this.changeCapacity = changeCapacity;
        this.markAttendance = markAttendance;
        this.getRoster = getRoster;
    }

    /** The sessions the caller may book in the Lisbon week (Monday to Sunday) that contains {@code weekOf}; this week when it is absent. */
    @GetMapping
    public List<BookableSessionResponse> week(@CurrentActor AuthenticatedActor caller,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PlausibleDate LocalDate weekOf) {
        return BookingWebMapper.toResponses(listBookableSessions.execute(BookingWebMapper.weekQuery(caller.actor(), weekOf)));
    }

    @PostMapping("/{sessionId}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingPlacedResponse book(@CurrentActor AuthenticatedActor caller, @PathVariable UUID sessionId) {
        return BookingWebMapper.toResponse(bookSession.execute(BookingWebMapper.bookCommand(caller.actor(), sessionId)));
    }

    /** The booking stays in the member's history (as CANCELLED), so cancelling is something that is done to it, not a delete. */
    @PostMapping("/{sessionId}/bookings/{bookingId}/cancellation")
    public BookingCancelledResponse cancelBooking(@CurrentActor AuthenticatedActor caller, @PathVariable UUID sessionId,
                                                  @PathVariable UUID bookingId) {
        return BookingWebMapper.toResponse(cancelBooking.execute(BookingWebMapper.cancelCommand(caller.actor(), sessionId, bookingId)));
    }

    @PostMapping("/{sessionId}/cancellation")
    public SessionCancelledResponse cancel(@CurrentActor AuthenticatedActor caller, @PathVariable UUID sessionId,
                                           @Valid @RequestBody CancelSessionRequest body) {
        return SessionWebMapper.toResponse(cancelSession.execute(SessionWebMapper.cancelCommand(caller.actor(), sessionId, body)));
    }

    @PutMapping("/{sessionId}/capacity")
    public CapacityChangedResponse capacity(@CurrentActor AuthenticatedActor caller, @PathVariable UUID sessionId,
                                            @Valid @RequestBody ChangeCapacityRequest body) {
        return SessionWebMapper.toResponse(changeCapacity.execute(SessionWebMapper.capacityCommand(caller.actor(), sessionId, body)));
    }

    /** Who is in the session, with the booking ids attendance is marked with: for the session's coach or an administrator (US-17). */
    @GetMapping("/{sessionId}/roster")
    public SessionRosterResponse roster(@CurrentActor AuthenticatedActor caller, @PathVariable UUID sessionId) {
        return SessionWebMapper.toResponse(getRoster.execute(SessionWebMapper.rosterQuery(caller.actor(), sessionId)));
    }

    @PutMapping("/{sessionId}/attendance")
    public AttendanceMarkedResponse attendance(@CurrentActor AuthenticatedActor caller, @PathVariable UUID sessionId,
                                               @Valid @RequestBody MarkAttendanceRequest body) {
        return SessionWebMapper.toResponse(markAttendance.execute(SessionWebMapper.attendanceCommand(caller.actor(), sessionId, body)));
    }
}
