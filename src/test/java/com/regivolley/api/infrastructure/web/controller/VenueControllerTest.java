package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.CreateVenueCommand;
import com.regivolley.api.application.command.DeleteVenueCommand;
import com.regivolley.api.application.command.EditVenueCommand;
import com.regivolley.api.application.result.VenueDeleted;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-02 over HTTP: venues. */
class VenueControllerTest extends AbstractControllerWebTest {

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

    // ---- venues ----------------------------------------------------------------------------------------------------

    @Test
    void creatingAVenueAnswers201WithItsLocation() throws Exception {
        // Arrange
        when(createVenueUseCase.execute(any())).thenReturn(venue);

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/venues", "{\"name\":\"Pavilion One\",\"address\":\"Rua A 1, Lisbon\",\"courts\":2}");

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(venue.id().value().toString()))
                .andExpect(jsonPath("$.name").value("Pavilion One"))
                .andExpect(jsonPath("$.courts").value(2));
        ArgumentCaptor<CreateVenueCommand> command = ArgumentCaptor.forClass(CreateVenueCommand.class);
        verify(createVenueUseCase).execute(command.capture());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
        assertThat(command.getValue().address()).isEqualTo("Rua A 1, Lisbon");
        assertThat(command.getValue().courts()).isEqualTo(2);
    }

    @Test
    void aVenueWithoutNameAddressOrCourtsIs400() throws Exception {
        // Arrange
        String body = "{\"name\":\"\",\"address\":\"\"}";

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/venues", body);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@ == 'name')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'address')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'courts')]").exists());
        verifyNoInteractions(createVenueUseCase);
    }

    @Test
    void aVenueWithoutCourtsIs400() throws Exception {
        // Arrange
        String body = "{\"name\":\"P\",\"address\":\"A\",\"courts\":0}";

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/venues", body);

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("courts"));
        verifyNoInteractions(createVenueUseCase);
    }

    @Test
    void editingAVenuePassesThePathIdAndTheNewValues() throws Exception {
        // Arrange
        when(editVenueUseCase.execute(any())).thenReturn(venue.edit("Pavilion Two", "Rua B 2", 3));

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/venues/" + venue.id(), "{\"name\":\"Pavilion Two\",\"address\":\"Rua B 2\",\"courts\":3}");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Pavilion Two")).andExpect(jsonPath("$.courts").value(3));
        ArgumentCaptor<EditVenueCommand> command = ArgumentCaptor.forClass(EditVenueCommand.class);
        verify(editVenueUseCase).execute(command.capture());
        assertThat(command.getValue().venueId()).isEqualTo(venue.id());
    }

    @Test
    void deletingAVenueAnswers204() throws Exception {
        // Arrange
        when(deleteVenueUseCase.execute(any())).thenReturn(new VenueDeleted(venue.id()));

        // Act
        var result = authenticated(HttpMethod.DELETE, "/api/v1/venues/" + venue.id());

        // Assert
        result.andExpect(status().isNoContent());
        ArgumentCaptor<DeleteVenueCommand> command = ArgumentCaptor.forClass(DeleteVenueCommand.class);
        verify(deleteVenueUseCase).execute(command.capture());
        assertThat(command.getValue().venueId()).isEqualTo(venue.id());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }
}
