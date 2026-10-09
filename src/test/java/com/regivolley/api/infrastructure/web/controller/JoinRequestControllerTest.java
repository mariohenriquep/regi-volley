package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.ApproveJoinRequestCommand;
import com.regivolley.api.application.command.ListPendingJoinRequestsQuery;
import com.regivolley.api.application.command.RejectJoinRequestCommand;
import com.regivolley.api.application.result.JoinRequestApproval;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.factory.MemberFactory;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-06 over HTTP: an administrator lists, approves and rejects join requests. */
class JoinRequestControllerTest extends AbstractControllerWebTest {

    private Association other;
    private JoinRequest request;

    @BeforeEach
    void setUpRequest() {
        other = WebFixtures.association();
        request = WebFixtures.joinRequest(other);
    }

    @Test
    void thePendingRequestsAreListedWithTheApplicantsContactData() throws Exception {
        // Arrange
        when(listPendingJoinRequestsUseCase.execute(any())).thenReturn(List.of(request));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/join-requests");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(request.id().value().toString()))
                .andExpect(jsonPath("$[0].name").value("Rita Costa"))
                .andExpect(jsonPath("$[0].email").value("rita@example.com"))
                .andExpect(jsonPath("$[0].phone").value("912345679"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].policyVersion").value("2026-01"))
                .andExpect(jsonPath("$[0].decidedAt").doesNotExist());
        ArgumentCaptor<ListPendingJoinRequestsQuery> query = ArgumentCaptor.forClass(ListPendingJoinRequestsQuery.class);
        verify(listPendingJoinRequestsUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void approvingAnswersWithTheApprovedRequestAndTheNewMembersId() throws Exception {
        // Arrange
        Member admin = WebFixtures.member(other);
        JoinRequest approved = request.approve(admin.id(), WebFixtures.CLOCK);
        Member created = MemberFactory.fromApprovedJoinRequest(other, approved);
        when(approveJoinRequestUseCase.execute(any())).thenReturn(new JoinRequestApproval(approved, created));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/join-requests/" + request.id() + "/approval");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.request.decidedAt").value("2026-10-12T09:00:00Z"))
                .andExpect(jsonPath("$.memberId").value(created.id().value().toString()));
        ArgumentCaptor<ApproveJoinRequestCommand> command = ArgumentCaptor.forClass(ApproveJoinRequestCommand.class);
        verify(approveJoinRequestUseCase).execute(command.capture());
        assertThat(command.getValue().requestId()).isEqualTo(request.id());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void rejectingPassesTheReasonAndAnswersWithTheRejectedRequest() throws Exception {
        // Arrange
        when(rejectJoinRequestUseCase.execute(any())).thenReturn(request.reject(MemberId.generate(), "Club is full", WebFixtures.CLOCK));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/join-requests/" + request.id() + "/rejection", "{\"reason\":\"Club is full\"}");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Club is full"));
        ArgumentCaptor<RejectJoinRequestCommand> command = ArgumentCaptor.forClass(RejectJoinRequestCommand.class);
        verify(rejectJoinRequestUseCase).execute(command.capture());
        assertThat(command.getValue().reason()).isEqualTo("Club is full");
        assertThat(command.getValue().requestId()).isEqualTo(request.id());
    }

    @Test
    void aTooLongReasonOrAnUnknownPropertyIs400() throws Exception {
        // Arrange
        String path = "/api/v1/join-requests/" + request.id() + "/rejection";

        // Act
        var tooLong = authenticated(HttpMethod.POST, path, "{\"reason\":\"" + "r".repeat(501) + "\"}");
        var unknown = authenticated(HttpMethod.POST, path, "{\"reason\":\"x\",\"status\":\"APPROVED\"}");

        // Assert
        tooLong.andExpect(status().isBadRequest());
        unknown.andExpect(status().isBadRequest());
    }
}
