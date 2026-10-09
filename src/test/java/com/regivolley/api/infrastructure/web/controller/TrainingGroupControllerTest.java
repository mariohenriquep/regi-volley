package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.ArchiveTrainingGroupCommand;
import com.regivolley.api.application.command.CreateTrainingGroupCommand;
import com.regivolley.api.application.command.EditTrainingGroupCommand;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-09 over HTTP: training groups. */
class TrainingGroupControllerTest extends AbstractControllerWebTest {

    private Association other;
    private Venue venue;
    private Member coach;
    private TrainingGroup group;

    @BeforeEach
    void setUpDomain() {
        other = WebFixtures.association();
        venue = WebFixtures.venue(other);
        coach = WebFixtures.member(other, MemberRole.COACH);
        group = WebFixtures.group(other, venue, coach.id());
    }

    private String groupBody(boolean withVenue) {
        return "{\"name\":\"Wednesday Beginners\",\"acceptedLevelIds\":[\"" + other.entryLevelId() + "\"],"
                + (withVenue ? "\"venueId\":\"" + venue.id() + "\"," : "")
                + "\"schedule\":[{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"20:00\",\"durationMinutes\":90}],"
                + "\"capacity\":12,\"coachId\":\"" + coach.id() + "\"}";
    }


    @Test
    void creatingAGroupMapsTheScheduleToLisbonSlotsAndAnswers201() throws Exception {
        // Arrange
        when(createTrainingGroupUseCase.execute(any())).thenReturn(group);

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/training-groups", groupBody(true));

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.venueId").value(venue.id().value().toString()))
                .andExpect(jsonPath("$.schedule[0].dayOfWeek").value("WEDNESDAY"))
                .andExpect(jsonPath("$.schedule[0].startTime").value("20:00"))
                .andExpect(jsonPath("$.schedule[0].durationMinutes").value(90))
                .andExpect(jsonPath("$.capacity").value(12))
                .andExpect(jsonPath("$.coachId").value(coach.id().value().toString()));
        ArgumentCaptor<CreateTrainingGroupCommand> command = ArgumentCaptor.forClass(CreateTrainingGroupCommand.class);
        verify(createTrainingGroupUseCase).execute(command.capture());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
        assertThat(command.getValue().venueId()).isEqualTo(venue.id());
        assertThat(command.getValue().coachId()).isEqualTo(coach.id());
        assertThat(command.getValue().acceptedLevels()).containsExactly(other.entryLevelId());
        assertThat(command.getValue().schedule().slots()).singleElement().satisfies(slot -> {
            assertThat(slot.dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
            assertThat(slot.startTime()).isEqualTo(LocalTime.of(20, 0));
            assertThat(slot.duration()).isEqualTo(Duration.ofMinutes(90));
        });
    }

    @Test
    void overlappingSlotsAreARuleViolationNotAServerError() throws Exception {
        // Arrange
        String body = groupBody(true).replace("\"schedule\":[{", "\"schedule\":[{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"20:30\",\"durationMinutes\":60},{");

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/training-groups", body);

        // Assert
        result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        verifyNoInteractions(createTrainingGroupUseCase);
    }

    @Test
    void aGroupNeedsItsVenueLevelsScheduleAndCoach() throws Exception {
        // Arrange
        String body = "{\"name\":\"G\",\"acceptedLevelIds\":[],\"schedule\":[],\"capacity\":0}";

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/training-groups", body);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@ == 'acceptedLevelIds')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'venueId')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'schedule')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'capacity')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'coachId')]").exists());
        verifyNoInteractions(createTrainingGroupUseCase);
    }

    @Test
    void aSlotWithAnImpossibleDurationOrAnUnknownDayIs400() throws Exception {
        // Arrange
        String longSlot = groupBody(true).replace("\"durationMinutes\":90", "\"durationMinutes\":600");
        String badDay = groupBody(true).replace("WEDNESDAY", "FUNDAY");

        // Act
        var first = authenticated(HttpMethod.POST, "/api/v1/training-groups", longSlot);
        var second = authenticated(HttpMethod.POST, "/api/v1/training-groups", badDay);

        // Assert
        first.andExpect(status().isBadRequest());
        second.andExpect(status().isBadRequest());
        verifyNoInteractions(createTrainingGroupUseCase);
    }

    @Test
    void editingAGroupCannotChangeItsVenue() throws Exception {
        // Arrange
        when(editTrainingGroupUseCase.execute(any())).thenReturn(group.rename("Renamed"));

        // Act
        var withVenue = authenticated(HttpMethod.PUT, "/api/v1/training-groups/" + group.id(), groupBody(true));
        var withoutVenue = authenticated(HttpMethod.PUT, "/api/v1/training-groups/" + group.id(), groupBody(false));

        // Assert
        withVenue.andExpect(status().isBadRequest());
        withoutVenue.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"));
        ArgumentCaptor<EditTrainingGroupCommand> command = ArgumentCaptor.forClass(EditTrainingGroupCommand.class);
        verify(editTrainingGroupUseCase).execute(command.capture());
        assertThat(command.getValue().groupId()).isEqualTo(group.id());
        assertThat(command.getValue().capacity()).isEqualTo(12);
    }

    @Test
    void archivingAGroupAnswersWithItArchived() throws Exception {
        // Arrange
        when(archiveTrainingGroupUseCase.execute(any())).thenReturn(group.archive());

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/training-groups/" + group.id() + "/archival");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        ArgumentCaptor<ArchiveTrainingGroupCommand> command = ArgumentCaptor.forClass(ArchiveTrainingGroupCommand.class);
        verify(archiveTrainingGroupUseCase).execute(command.capture());
        assertThat(command.getValue().groupId()).isEqualTo(group.id());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }
}