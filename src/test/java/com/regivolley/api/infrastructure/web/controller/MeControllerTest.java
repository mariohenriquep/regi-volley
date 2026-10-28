package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.GetMyRolesQuery;
import com.regivolley.api.application.result.MyRoles;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /me}: the ids the token resolved plus the roles the member holds now. */
class MeControllerTest extends AbstractControllerWebTest {

    @Test
    void meCarriesTheIdsFromTheTokenAndTheRolesFromTheUseCase() throws Exception {
        // Arrange
        when(getMyRolesUseCase.execute(any())).thenReturn(new MyRoles(List.of(MemberRole.MEMBER, MemberRole.COACH, MemberRole.ADMIN)));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/me");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.id().value().toString()))
                .andExpect(jsonPath("$.associationId").value(association.id().value().toString()))
                .andExpect(jsonPath("$.roles.length()").value(3))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.roles[1]").value("COACH"))
                .andExpect(jsonPath("$.roles[2]").value("ADMIN"));
        ArgumentCaptor<GetMyRolesQuery> query = ArgumentCaptor.forClass(GetMyRolesQuery.class);
        verify(getMyRolesUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void aPlainMemberGetsOnlyMember() throws Exception {
        // Arrange
        when(getMyRolesUseCase.execute(any())).thenReturn(new MyRoles(List.of(MemberRole.MEMBER)));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/me");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(1)).andExpect(jsonPath("$.roles[0]").value("MEMBER"));
    }

    @Test
    void withoutATokenItIs401AndNoUseCaseRuns() throws Exception {
        // Arrange
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me");

        // Act
        var result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isUnauthorized());
        verifyNoInteractions(getMyRolesUseCase);
    }
}
