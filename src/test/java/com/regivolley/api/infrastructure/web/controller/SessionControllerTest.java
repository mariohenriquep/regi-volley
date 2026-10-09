package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.AttendanceMark;
import com.regivolley.api.application.command.BookSessionCommand;
import com.regivolley.api.application.command.CancelBookingCommand;
import com.regivolley.api.application.command.CancelSessionCommand;
import com.regivolley.api.application.command.ChangeSessionCapacityCommand;
import com.regivolley.api.application.command.ListBookableSessionsQuery;
import com.regivolley.api.application.command.MarkAttendanceCommand;
import com.regivolley.api.application.result.AttendanceMarked;
import com.regivolley.api.application.result.BookableSession;
import com.regivolley.api.application.result.CancelledBooking;
import com.regivolley.api.application.result.CancelledSession;
import com.regivolley.api.application.result.CapacityChanged;
import com.regivolley.api.application.result.PlacedBooking;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-11 to US-18 over HTTP: listing the week, booking, cancelling, and the staff's session operations. */
class SessionControllerTest extends AbstractControllerWebTest {

    private Session session;
    private Booking booking;
    private Member coach;

    @BeforeEach
    void setUpSession() {
        Association other = WebFixtures.association();
        Venue venue = WebFixtures.venue(other);
        coach = WebFixtures.member(other, MemberRole.COACH);
        TrainingGroup group = WebFixtures.group(other, venue, coach.id());
        session = WebFixtures.bookedSession(other, group, member.id());
        booking = session.bookings().get(0);
    }

    @Test
    void theWeekListsTheBookableSessionsForTheCallerWithTheirOwnBookingStatus() throws Exception {
        // Arrange
        Instant start = Instant.parse("2026-10-14T19:00:00Z");
        when(listBookableSessionsUseCase.execute(any())).thenReturn(List.of(new BookableSession(SessionId.of(session.id().value()),
                session.trainingGroupId(), "Wednesday Beginners", start, start.plusSeconds(5400), start.minusSeconds(86400 * 7), 12, 11, 0,
                Optional.of(BookingStatus.CONFIRMED))));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/sessions?weekOf=2026-10-14");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(session.id().value().toString()))
                .andExpect(jsonPath("$[0].groupName").value("Wednesday Beginners"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-10-14T19:00:00Z"))
                .andExpect(jsonPath("$[0].freeSeats").value(11))
                .andExpect(jsonPath("$[0].myBookingStatus").value("CONFIRMED"));
        ArgumentCaptor<ListBookableSessionsQuery> query = ArgumentCaptor.forClass(ListBookableSessionsQuery.class);
        verify(listBookableSessionsUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
        assertThat(query.getValue().dayInWeek()).isEqualTo(LocalDate.parse("2026-10-14"));
    }

    @Test
    void withoutADateTheCurrentWeekIsAskedForAndAFreeSessionHasNoBookingStatus() throws Exception {
        // Arrange
        Instant start = Instant.parse("2026-10-14T19:00:00Z");
        when(listBookableSessionsUseCase.execute(any())).thenReturn(List.of(new BookableSession(session.id(), session.trainingGroupId(),
                "Group", start, start.plusSeconds(5400), start, 12, 12, 0, Optional.empty())));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/sessions");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$[0].myBookingStatus").doesNotExist());
        ArgumentCaptor<ListBookableSessionsQuery> query = ArgumentCaptor.forClass(ListBookableSessionsQuery.class);
        verify(listBookableSessionsUseCase).execute(query.capture());
        assertThat(query.getValue().dayInWeek()).isNull();
    }

    @Test
    void aMalformedWeekIs400() throws Exception {
        // Arrange
        String path = "/api/v1/sessions?weekOf=next-tuesday";

        // Act
        var result = authenticated(HttpMethod.GET, path);

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(listBookableSessionsUseCase);
    }

    @Test
    void aWeekOutsideTheYears2000To2100Is400() throws Exception {
        // Arrange
        when(listBookableSessionsUseCase.execute(any())).thenReturn(List.of());

        // Act
        var tooEarly = authenticated(HttpMethod.GET, "/api/v1/sessions?weekOf=1999-12-31");
        var tooLate = authenticated(HttpMethod.GET, "/api/v1/sessions?weekOf=2101-01-01");
        var edge = authenticated(HttpMethod.GET, "/api/v1/sessions?weekOf=2100-12-31");

        // Assert
        tooEarly.andExpect(status().isBadRequest());
        tooLate.andExpect(status().isBadRequest());
        edge.andExpect(status().isOk());
    }

    @Test
    void bookingPlacesTheCallersOwnSeatAndAnswers201() throws Exception {
        // Arrange
        when(bookSessionUseCase.execute(any())).thenReturn(new PlacedBooking(session, booking, OptionalInt.empty()));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/sessions/" + session.id() + "/bookings");

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(session.id().value().toString()))
                .andExpect(jsonPath("$.bookingId").value(booking.id().value().toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.waitlistPosition").doesNotExist());
        ArgumentCaptor<BookSessionCommand> command = ArgumentCaptor.forClass(BookSessionCommand.class);
        verify(bookSessionUseCase).execute(command.capture());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
        assertThat(command.getValue().sessionId()).isEqualTo(session.id());
    }

    @Test
    void aWaitlistedBookingShowsItsPlaceInTheQueue() throws Exception {
        // Arrange
        when(bookSessionUseCase.execute(any())).thenReturn(new PlacedBooking(session, booking, OptionalInt.of(3)));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/sessions/" + session.id() + "/bookings");

        // Assert
        result.andExpect(status().isCreated()).andExpect(jsonPath("$.waitlistPosition").value(3));
    }

    @Test
    void aSessionIdThatIsNotAUuidIs400() throws Exception {
        // Arrange
        String path = "/api/v1/sessions/not-a-uuid/bookings";

        // Act
        var result = authenticated(HttpMethod.POST, path);

        // Assert
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(bookSessionUseCase);
    }

    @Test
    void cancellingABookingReportsLatenessRefundAndHowManyMovedUp() throws Exception {
        // Arrange
        when(cancelBookingUseCase.execute(any())).thenReturn(new CancelledBooking(session, booking, true, false, List.of(booking)));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/sessions/" + session.id() + "/bookings/" + booking.id() + "/cancellation");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(booking.id().value().toString()))
                .andExpect(jsonPath("$.late").value(true))
                .andExpect(jsonPath("$.creditRefunded").value(false))
                .andExpect(jsonPath("$.promotedCount").value(1));
        ArgumentCaptor<CancelBookingCommand> command = ArgumentCaptor.forClass(CancelBookingCommand.class);
        verify(cancelBookingUseCase).execute(command.capture());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
        assertThat(command.getValue().sessionId()).isEqualTo(session.id());
        assertThat(command.getValue().bookingId()).isEqualTo(booking.id());
    }

    @Test
    void cancellingASessionPassesTheReasonAndReportsTheConsequences() throws Exception {
        // Arrange
        when(cancelSessionUseCase.execute(any())).thenReturn(new CancelledSession(session.cancel("Pavilion closed"), List.of(booking), 1));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/sessions/" + session.id() + "/cancellation", "{\"reason\":\"Pavilion closed\"}");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("CANCELLED"))
                .andExpect(jsonPath("$.session.cancellationReason").value("Pavilion closed"))
                .andExpect(jsonPath("$.bookingsCancelled").value(1))
                .andExpect(jsonPath("$.creditsRefunded").value(1));
        ArgumentCaptor<CancelSessionCommand> command = ArgumentCaptor.forClass(CancelSessionCommand.class);
        verify(cancelSessionUseCase).execute(command.capture());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
        assertThat(command.getValue().reason()).isEqualTo("Pavilion closed");
    }

    @Test
    void cancellingASessionRefusesAnUnknownPropertyAndATooLongReason() throws Exception {
        // Arrange
        String unknown = "{\"reason\":\"x\",\"status\":\"SCHEDULED\"}";
        String tooLong = "{\"reason\":\"" + "x".repeat(501) + "\"}";

        // Act
        var first = authenticated(HttpMethod.POST, "/api/v1/sessions/" + session.id() + "/cancellation", unknown);
        var second = authenticated(HttpMethod.POST, "/api/v1/sessions/" + session.id() + "/cancellation", tooLong);

        // Assert
        first.andExpect(status().isBadRequest());
        second.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("reason"));
        verifyNoInteractions(cancelSessionUseCase);
    }

    @Test
    void changingTheCapacityShowsTheSessionAndHowManyMovedUp() throws Exception {
        // Arrange
        when(changeSessionCapacityUseCase.execute(any())).thenReturn(new CapacityChanged(session, List.of(booking)));

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/sessions/" + session.id() + "/capacity", "{\"capacity\":14}");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.session.id").value(session.id().value().toString()))
                .andExpect(jsonPath("$.session.bookings[0].memberId").value(member.id().value().toString()))
                .andExpect(jsonPath("$.promotedCount").value(1));
        ArgumentCaptor<ChangeSessionCapacityCommand> command = ArgumentCaptor.forClass(ChangeSessionCapacityCommand.class);
        verify(changeSessionCapacityUseCase).execute(command.capture());
        assertThat(command.getValue().capacity()).isEqualTo(14);
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void aCapacityOfZeroOrNothingIs400() throws Exception {
        // Arrange
        String path = "/api/v1/sessions/" + session.id() + "/capacity";

        // Act
        var zero = authenticated(HttpMethod.PUT, path, "{\"capacity\":0}");
        var missing = authenticated(HttpMethod.PUT, path, "{}");

        // Assert
        zero.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("capacity"));
        missing.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("capacity"));
        verifyNoInteractions(changeSessionCapacityUseCase);
    }

    @Test
    void markingAttendancePassesEveryEntryAndReportsTheWarnings() throws Exception {
        // Arrange
        BookingId other = BookingId.generate();
        when(markAttendanceUseCase.execute(any())).thenReturn(new AttendanceMarked(session, List.of(MemberId.generate())));
        String body = "{\"entries\":[{\"bookingId\":\"" + booking.id() + "\",\"mark\":\"ATTENDED\"},{\"bookingId\":\"" + other
                + "\",\"mark\":\"NO_SHOW\"}]}";

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/sessions/" + session.id() + "/attendance", body);

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.warnedCount").value(1));
        ArgumentCaptor<MarkAttendanceCommand> command = ArgumentCaptor.forClass(MarkAttendanceCommand.class);
        verify(markAttendanceUseCase).execute(command.capture());
        assertThat(command.getValue().entries()).hasSize(2);
        assertThat(command.getValue().entries().get(1).bookingId()).isEqualTo(other);
        assertThat(command.getValue().entries().get(1).mark()).isEqualTo(AttendanceMark.NO_SHOW);
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void attendanceNeedsEntriesAndKnownMarks() throws Exception {
        // Arrange
        String path = "/api/v1/sessions/" + session.id() + "/attendance";
        String bad = "{\"entries\":[{\"bookingId\":\"" + UUID.randomUUID() + "\",\"mark\":\"LATE\"}]}";

        // Act
        var empty = authenticated(HttpMethod.PUT, path, "{\"entries\":[]}");
        var unknownMark = authenticated(HttpMethod.PUT, path, bad);

        // Assert
        empty.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("entries"));
        unknownMark.andExpect(status().isBadRequest());
        verifyNoInteractions(markAttendanceUseCase);
    }
}
