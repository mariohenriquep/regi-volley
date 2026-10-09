package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.CreatePlanCommand;
import com.regivolley.api.application.command.EditPlanCommand;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.PlanType;
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

/** US-19, RN-13 over HTTP: plans, with the price in cents. */
class PlanControllerTest extends AbstractControllerWebTest {

    private Association other;
    private Plan pack;

    @BeforeEach
    void setUpPlan() {
        other = WebFixtures.association();
        pack = WebFixtures.pack(other);
    }

    private String packBody() {
        return "{\"name\":\"Ten sessions\",\"type\":\"PACK\",\"credits\":10,\"allowedLevelIds\":[\"" + other.entryLevelId()
                + "\"],\"priceCents\":4500,\"validityDays\":90}";
    }

    @Test
    void creatingAPackMapsTheTermsAndThePriceAndAnswers201() throws Exception {
        // Arrange
        when(createPlanUseCase.execute(any())).thenReturn(pack);

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/plans", packBody());

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.type").value("PACK"))
                .andExpect(jsonPath("$.credits").value(10))
                .andExpect(jsonPath("$.sessionsPerWeek").doesNotExist())
                .andExpect(jsonPath("$.priceCents").value(4500))
                .andExpect(jsonPath("$.validityDays").value(90))
                .andExpect(jsonPath("$.allowedLevelIds[0]").value(other.entryLevelId().value().toString()));
        ArgumentCaptor<CreatePlanCommand> command = ArgumentCaptor.forClass(CreatePlanCommand.class);
        verify(createPlanUseCase).execute(command.capture());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
        assertThat(command.getValue().terms().type()).isEqualTo(PlanType.PACK);
        assertThat(command.getValue().terms().credits()).isEqualTo(10);
        assertThat(command.getValue().price().cents()).isEqualTo(4500);
        assertThat(command.getValue().validityDays()).isEqualTo(90);
    }

    @Test
    void aMonthlyPlanHasNoCreditsOrValidityInTheAnswer() throws Exception {
        // Arrange
        when(createPlanUseCase.execute(any())).thenReturn(WebFixtures.monthly(other));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/plans", "{\"name\":\"Monthly\",\"type\":\"MONTHLY_UNLIMITED\",\"priceCents\":3000}");

        // Assert
        result.andExpect(status().isCreated()).andExpect(jsonPath("$.credits").doesNotExist())
                .andExpect(jsonPath("$.validityDays").doesNotExist()).andExpect(jsonPath("$.allowedLevelIds").isEmpty());
        ArgumentCaptor<CreatePlanCommand> command = ArgumentCaptor.forClass(CreatePlanCommand.class);
        verify(createPlanUseCase).execute(command.capture());
        assertThat(command.getValue().terms().allowedLevels()).isEmpty();
    }

    @Test
    void termsThatDoNotGoWithTheTypeAre422NamingTheFieldWithTheDomainsMessage() throws Exception {
        // Arrange
        String body = "{\"name\":\"Odd\",\"type\":\"PACK\",\"priceCents\":100,\"validityDays\":30}";

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/plans", body);

        // Assert
        result.andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD"))
                .andExpect(jsonPath("$.fields[0]").value("credits"))
                .andExpect(jsonPath("$.message").value("A PACK plan needs credits of at least 1"));
        verifyNoInteractions(createPlanUseCase);
    }

    @Test
    void aPlanWithoutNameTypeOrPriceOrWithANegativePriceIs400() throws Exception {
        // Arrange
        String missing = "{\"name\":\" \"}";
        String negative = packBody().replace("4500", "-1");

        // Act
        var first = authenticated(HttpMethod.POST, "/api/v1/plans", missing);
        var second = authenticated(HttpMethod.POST, "/api/v1/plans", negative);

        // Assert
        first.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@ == 'name')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'type')]").exists())
                .andExpect(jsonPath("$.fields[?(@ == 'priceCents')]").exists());
        second.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("priceCents"));
        verifyNoInteractions(createPlanUseCase);
    }

    @Test
    void anUnknownPlanTypeIs400() throws Exception {
        // Arrange
        String body = packBody().replace("PACK", "LIFETIME");

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/plans", body);

        // Assert
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(createPlanUseCase);
    }

    @Test
    void editingAPlanPassesThePathIdAndReplacesItsTerms() throws Exception {
        // Arrange
        when(editPlanUseCase.execute(any())).thenReturn(pack.edit("Ten sessions", pack.terms(), com.regivolley.api.domain.model.valueobject.Money.ofCents(5000), 90));

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/plans/" + pack.id(), packBody().replace("4500", "5000"));

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.priceCents").value(5000));
        ArgumentCaptor<EditPlanCommand> command = ArgumentCaptor.forClass(EditPlanCommand.class);
        verify(editPlanUseCase).execute(command.capture());
        assertThat(command.getValue().planId()).isEqualTo(pack.id());
        assertThat(command.getValue().price().cents()).isEqualTo(5000);
    }
}
