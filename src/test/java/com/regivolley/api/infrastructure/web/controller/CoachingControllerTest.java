package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.ListCoachingSessionsQuery;
import com.regivolley.api.application.result.CoachingSession;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.exception.NotAllowedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The staff's week (US-11, US-12, US-17) over HTTP: the line, the optional names, the week asked for and the bounds of the date. */
class CoachingControllerTest extends AbstractControllerWebTest {

    private static final Instant START = Instant.parse("2026-10-14T19:00:00Z");

    private static CoachingSession line(Optional<String> group, Optional<String> venue) {
        return new CoachingSession(SessionId.generate(), TrainingGroupId.generate(), group, venue, MemberId.generate(), START,
                START.plusSeconds(5400), SessionStatus.SCHEDULED, 12, 9, 2, 1, 3, 5, true, false, true);
    }

    @Test
    void theWeekListsTheLineWithItsCountsAndActionFlags() throws Exception {
        // Arrange
        CoachingSession line = line(Optional.of("Wednesday Beginners"), Optional.of("Pavilhao Central"));
        when(listCoachingSessionsUseCase.execute(any())).thenReturn(List.of(line));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=2026-10-12");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(line.sessionId().value().toString()))
                .andExpect(jsonPath("$[0].trainingGroupId").value(line.trainingGroupId().value().toString()))
                .andExpect(jsonPath("$[0].groupName").value("Wednesday Beginners"))
                .andExpect(jsonPath("$[0].venueName").value("Pavilhao Central"))
                .andExpect(jsonPath("$[0].coachId").value(line.coachId().value().toString()))
                .andExpect(jsonPath("$[0].startsAt").value("2026-10-14T19:00:00Z"))
                .andExpect(jsonPath("$[0].endsAt").value("2026-10-14T20:30:00Z"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].capacity").value(12))
                .andExpect(jsonPath("$[0].confirmedCount").value(9))
                .andExpect(jsonPath("$[0].waitlistCount").value(2))
                .andExpect(jsonPath("$[0].attendedCount").value(1))
                .andExpect(jsonPath("$[0].noShowCount").value(3))
                .andExpect(jsonPath("$[0].unmarkedCount").value(5))
                .andExpect(jsonPath("$[0].canMarkAttendance").value(true))
                .andExpect(jsonPath("$[0].canCancel").value(false))
                .andExpect(jsonPath("$[0].canChangeCapacity").value(true));
        ArgumentCaptor<ListCoachingSessionsQuery> query = ArgumentCaptor.forClass(ListCoachingSessionsQuery.class);
        verify(listCoachingSessionsUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
        assertThat(query.getValue().dayInWeek()).isEqualTo(LocalDate.parse("2026-10-12"));
    }

    @Test
    void withoutADateTheCurrentWeekIsAskedForAndUnknownNamesAreLeftOut() throws Exception {
        // Arrange
        when(listCoachingSessionsUseCase.execute(any())).thenReturn(List.of(line(Optional.empty(), Optional.empty())));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/coaching/sessions");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupName").doesNotExist())
                .andExpect(jsonPath("$[0].venueName").doesNotExist());
        ArgumentCaptor<ListCoachingSessionsQuery> query = ArgumentCaptor.forClass(ListCoachingSessionsQuery.class);
        verify(listCoachingSessionsUseCase).execute(query.capture());
        assertThat(query.getValue().dayInWeek()).isNull();
    }

    @Test
    void anEmptyWeekIsAnEmptyArray() throws Exception {
        // Arrange
        when(listCoachingSessionsUseCase.execute(any())).thenReturn(List.of());

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/coaching/sessions");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aCallerWhoIsNotStaffGets403() throws Exception {
        // Arrange
        when(listCoachingSessionsUseCase.execute(any())).thenThrow(new NotAllowedException("see the coaching sessions"));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/coaching/sessions");

        // Assert
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ALLOWED"));
    }

    @Test
    void aMalformedWeekIs400() throws Exception {
        // Arrange
        String path = "/api/v1/coaching/sessions?weekOf=next-tuesday";

        // Act
        var result = authenticated(HttpMethod.GET, path);

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(listCoachingSessionsUseCase);
    }

    @Test
    void aWeekOutsideTheYears2000To2100Is400AndTheEdgesAreAccepted() throws Exception {
        // Arrange
        when(listCoachingSessionsUseCase.execute(any())).thenReturn(List.of());

        // Act
        var tooEarly = authenticated(HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=1999-12-31");
        var tooLate = authenticated(HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=2101-01-01");
        var edge = authenticated(HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=2100-12-31");

        // Assert
        tooEarly.andExpect(status().isBadRequest());
        tooLate.andExpect(status().isBadRequest());
        edge.andExpect(status().isOk());
    }

    @Test
    void theTenantAndTheRolesCannotBeSuppliedByTheClient() throws Exception {
        // Arrange
        when(listCoachingSessionsUseCase.execute(any())).thenReturn(List.of());

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/coaching/sessions?associationId=" + java.util.UUID.randomUUID() + "&coachId=" + java.util.UUID.randomUUID());

        // Assert
        result.andExpect(status().isOk());
        ArgumentCaptor<ListCoachingSessionsQuery> query = ArgumentCaptor.forClass(ListCoachingSessionsQuery.class);
        verify(listCoachingSessionsUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void withoutATokenItIs401() throws Exception {
        // Arrange
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/coaching/sessions");

        // Act
        var result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isUnauthorized());
        verifyNoInteractions(listCoachingSessionsUseCase);
    }
}
