package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.GetPublicAssociationQuery;
import com.regivolley.api.application.command.RegisterAssociationCommand;
import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.result.AssociationRegistered;
import com.regivolley.api.application.result.JoinRequestSubmitted;
import com.regivolley.api.application.result.PublicAssociationPage;
import com.regivolley.api.application.result.PublicGroup;
import com.regivolley.api.application.result.PublicLevel;
import com.regivolley.api.application.result.PublicSlot;
import com.regivolley.api.application.result.PublicVenue;
import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.ShortName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-24, US-01, US-05 over HTTP: the endpoints that need no token (threat model P1, P4, P6, D-14). */
class PublicAssociationControllerTest extends AbstractControllerWebTest {

    private static final String REGISTER = """
            {"name":"Club Voleibol","shortName":" Club-Voley ","nif":"123456789","locality":"Lisbon","contactEmail":"info@club.example",
             "levelNames":["Beginner","Advanced"],"founderName":"Ana Silva","founderEmail":"ana@example.com","founderPhone":"912345678",
             "consentAccepted":true,"policyVersion":"2026-01"}""";
    private static final String JOIN = """
            {"name":"Rita Costa","email":"rita@example.com","phone":"912345679","consentAccepted":true,"policyVersion":"2026-01"}""";

    // ---- the public page -------------------------------------------------------------------------------------------

    @Test
    void thePublicPageNeedsNoTokenAndShowsOnlyTheWhitelistedFields() throws Exception {
        // Arrange
        PublicAssociationPage page = new PublicAssociationPage("Club Voleibol", "club-voley", "Lisbon", "info@club.example",
                List.of(new PublicLevel("Beginner"), new PublicLevel("Advanced")),
                List.of(new PublicGroup("Wednesday", List.of("Beginner"), "Pavilion One",
                        List.of(new PublicSlot(DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), 90)))),
                List.of(new PublicVenue("Pavilion One", "Rua A 1", 2)));
        when(getPublicAssociationUseCase.execute(any())).thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/v1/public/associations/club-voley").with(fromNewClient())).andReturn();

        // Assert
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"name\":\"Club Voleibol\"").contains("\"contactEmail\":\"info@club.example\"")
                .contains("\"levels\":[\"Beginner\",\"Advanced\"]").contains("\"dayOfWeek\":\"WEDNESDAY\"")
                .contains("\"startTime\":\"20:00\"").contains("\"durationMinutes\":90").contains("\"venue\":\"Pavilion One\"")
                .doesNotContain("\"id\"").doesNotContain("Id\"").doesNotContain("coach").doesNotContain("member");
        ArgumentCaptor<GetPublicAssociationQuery> query = ArgumentCaptor.forClass(GetPublicAssociationQuery.class);
        verify(getPublicAssociationUseCase).execute(query.capture());
        assertThat(query.getValue().shortName()).isEqualTo("club-voley");
    }

    @Test
    void aStaleOrGarbageAuthorizationHeaderDoesNotTurnThePublicPageIntoA401() throws Exception {
        // Arrange
        when(getPublicAssociationUseCase.execute(any())).thenReturn(new PublicAssociationPage("Club", "club-voley", "Lisbon",
                "info@club.example", List.of(), List.of(), List.of()));

        // Act
        var result = mockMvc.perform(get("/api/v1/public/associations/club-voley").with(fromNewClient()).header("Authorization", "Bearer not-a-token"));

        // Assert
        result.andExpect(status().isOk());
    }

    @Test
    void anUnknownShortNameIs404() throws Exception {
        // Arrange
        when(getPublicAssociationUseCase.execute(any())).thenThrow(new ShortNameNotFoundException(ShortName.of("no-such-club")));

        // Act
        var result = mockMvc.perform(get("/api/v1/public/associations/no-such-club").with(fromNewClient()));

        // Assert
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("no-such-club"))));
    }

    // ---- registering an association --------------------------------------------------------------------------------

    @Test
    void registeringCreatesTheAssociationAndAnswersOnlyWithTheShortName() throws Exception {
        // Arrange
        when(registerAssociationUseCase.execute(any())).thenReturn(new AssociationRegistered(AssociationId.generate(), MemberId.generate()));

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations", REGISTER);

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/public/associations/club-voley"))
                .andExpect(content().json("{\"shortName\":\"club-voley\"}", true));
        ArgumentCaptor<RegisterAssociationCommand> command = ArgumentCaptor.forClass(RegisterAssociationCommand.class);
        verify(registerAssociationUseCase).execute(command.capture());
        assertThat(command.getValue().name()).isEqualTo("Club Voleibol");
        assertThat(command.getValue().levelNames()).containsExactly("Beginner", "Advanced");
        assertThat(command.getValue().founderEmail()).isEqualTo("ana@example.com");
        assertThat(command.getValue().consentAccepted()).isTrue();
    }

    @Test
    void theRegistrationAnswerIsTheSameWhateverTheFounderEmailWasToTheSystem() throws Exception {
        // Arrange
        when(registerAssociationUseCase.execute(any())).thenReturn(new AssociationRegistered(AssociationId.generate(), MemberId.generate()));
        String again = REGISTER.replace("ana@example.com", "someone.else@example.com");

        // Act
        String first = anonymous(HttpMethod.POST, "/api/v1/public/associations", REGISTER).andReturn().getResponse().getContentAsString();
        String second = anonymous(HttpMethod.POST, "/api/v1/public/associations", again).andReturn().getResponse().getContentAsString();

        // Assert
        assertThat(first).isEqualTo(second).isEqualTo("{\"shortName\":\"club-voley\"}");
    }

    @Test
    void aTakenShortNameIs409() throws Exception {
        // Arrange
        when(registerAssociationUseCase.execute(any())).thenThrow(new ShortNameAlreadyTakenException(ShortName.of("club-voley")));

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations", REGISTER);

        // Assert
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void registeringWithoutTheConsentIs422() throws Exception {
        // Arrange
        when(registerAssociationUseCase.execute(any())).thenThrow(new ConsentRequiredException());

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations", REGISTER.replace("\"consentAccepted\":true", "\"consentAccepted\":false"));

        // Assert
        result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void registeringWithMissingFieldsIs400NamingTheFieldsAndNotTheValues() throws Exception {
        // Arrange
        String body = "{\"name\":\"\",\"shortName\":\"club-voley\",\"locality\":\"Lisbon\",\"contactEmail\":\"info@club.example\","
                + "\"levelNames\":[],\"founderName\":\"Ana Silva\",\"founderEmail\":\"ana@example.com\",\"founderPhone\":\"912345678\","
                + "\"policyVersion\":\"2026-01\"}";

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations", body);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields[?(@ == 'name')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'levelNames')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'consentAccepted')]").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ana@example.com"))));
        verifyNoInteractions(registerAssociationUseCase);
    }

    @Test
    void registeringCannotCarryRolesATenantOrAnyUnknownProperty() throws Exception {
        // Arrange
        String withRoles = REGISTER.replace("\"policyVersion\"", "\"roles\":[\"ADMIN\"],\"associationId\":\"" + AssociationId.generate() + "\",\"policyVersion\"");

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations", withRoles);

        // Assert
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(registerAssociationUseCase);
    }

    // ---- asking to join --------------------------------------------------------------------------------------------

    @Test
    void aJoinRequestIsAcceptedWithTheUniformAcknowledgementAndNoId() throws Exception {
        // Arrange
        when(submitJoinRequestUseCase.execute(any())).thenReturn(new JoinRequestSubmitted(JoinRequestId.generate()));

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations/club-voley/join-requests", JOIN);

        // Assert
        result.andExpect(status().isAccepted()).andExpect(content().json("{\"status\":\"RECEIVED\"}", true));
        ArgumentCaptor<SubmitJoinRequestCommand> command = ArgumentCaptor.forClass(SubmitJoinRequestCommand.class);
        verify(submitJoinRequestUseCase).execute(command.capture());
        assertThat(command.getValue().shortName()).isEqualTo("club-voley");
        assertThat(command.getValue().email()).isEqualTo("rita@example.com");
        assertThat(command.getValue().consentAccepted()).isTrue();
    }

    @Test
    void aJoinRequestForAnAddressThatIsAlreadyAMemberOrPendingIsByteIdenticalToTheNewOne() throws Exception {
        // Arrange
        when(submitJoinRequestUseCase.execute(any())).thenReturn(new JoinRequestSubmitted(JoinRequestId.generate()))
                .thenThrow(new JoinRequestNotPossibleException());

        // Act
        var created = anonymous(HttpMethod.POST, "/api/v1/public/associations/club-voley/join-requests", JOIN).andReturn().getResponse();
        var known = anonymous(HttpMethod.POST, "/api/v1/public/associations/club-voley/join-requests", JOIN).andReturn().getResponse();

        // Assert
        assertThat(known.getStatus()).isEqualTo(created.getStatus()).isEqualTo(202);
        assertThat(known.getContentAsString()).isEqualTo(created.getContentAsString());
        assertThat(known.getContentType()).isEqualTo(created.getContentType());
    }

    @Test
    void aJoinRequestForAnUnknownAssociationIs404() throws Exception {
        // Arrange
        when(submitJoinRequestUseCase.execute(any())).thenThrow(new ShortNameNotFoundException(ShortName.of("no-such-club")));

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations/no-such-club/join-requests", JOIN);

        // Assert
        result.andExpect(status().isNotFound());
    }

    @Test
    void aJoinRequestOverThePerEmailLimitIs429WithRetryAfter() throws Exception {
        // Arrange
        when(submitJoinRequestUseCase.execute(any())).thenThrow(new RateLimitExceededException(Duration.ofHours(3)));

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations/club-voley/join-requests", JOIN);

        // Assert
        result.andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "10800"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void aJoinRequestWithoutConsentFieldOrWithABlankNameIs400AndNeverReachesTheUseCase() throws Exception {
        // Arrange
        String body = "{\"name\":\" \",\"email\":\"rita@example.com\",\"phone\":\"912345679\",\"policyVersion\":\"2026-01\"}";

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations/club-voley/join-requests", body);

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[?(@ == 'name')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'consentAccepted')]").exists());
        verify(submitJoinRequestUseCase, never()).execute(any());
    }

    @Test
    void aJoinRequestRefusesUnknownPropertiesSuchAsATenantOrAStatus() throws Exception {
        // Arrange
        String body = JOIN.replace("\"policyVersion\"", "\"status\":\"APPROVED\",\"policyVersion\"");

        // Act
        var result = anonymous(HttpMethod.POST, "/api/v1/public/associations/club-voley/join-requests", body);

        // Assert
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(submitJoinRequestUseCase);
    }

    @Test
    void theRequestDtosNeverPrintThePersonalDataTheyCarry() {
        // Arrange
        var join = new com.regivolley.api.infrastructure.web.dto.JoinAssociationRequest("Rita Costa", "rita@example.com", "912345679", true, "2026-01");

        // Act
        String printed = join.toString();

        // Assert
        assertThat(printed).doesNotContain("Rita").doesNotContain("rita@example.com").doesNotContain("912345679");
    }
}
