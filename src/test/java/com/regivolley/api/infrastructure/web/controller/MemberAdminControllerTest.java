package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.AssignPlanCommand;
import com.regivolley.api.application.command.ChangeMemberLevelCommand;
import com.regivolley.api.application.command.DeactivateMemberCommand;
import com.regivolley.api.application.command.GrantRoleCommand;
import com.regivolley.api.application.command.ResendActivationLinkCommand;
import com.regivolley.api.application.command.RevokeRoleCommand;
import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.result.MemberDeactivated;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-04, US-07, US-08, US-20 over HTTP: what an administrator does to a member. */
class MemberAdminControllerTest extends AbstractControllerWebTest {

    private Association other;
    private Member target;

    @BeforeEach
    void setUpTarget() {
        other = WebFixtures.association();
        target = WebFixtures.member(other, MemberRole.MEMBER);
    }

    @Test
    void changingALevelPassesTheTargetAndTheLevelAndShowsTheMember() throws Exception {
        // Arrange
        when(changeMemberLevelUseCase.execute(any())).thenReturn(target);

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/members/" + target.id() + "/level", "{\"levelId\":\"" + other.entryLevelId() + "\"}");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.id().value().toString()))
                .andExpect(jsonPath("$.name").value("Ana Silva"))
                .andExpect(jsonPath("$.email").value("ana.silva@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"));
        ArgumentCaptor<ChangeMemberLevelCommand> command = ArgumentCaptor.forClass(ChangeMemberLevelCommand.class);
        verify(changeMemberLevelUseCase).execute(command.capture());
        assertThat(command.getValue().memberId()).isEqualTo(target.id());
        assertThat(command.getValue().levelId()).isEqualTo(other.entryLevelId());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void grantingARoleUsesTheRoleInThePath() throws Exception {
        // Arrange
        when(grantRoleUseCase.execute(any())).thenReturn(target.grantRole(MemberRole.COACH));

        // Act
        var result = authenticated(HttpMethod.PUT, "/api/v1/members/" + target.id() + "/roles/COACH");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("MEMBER")).andExpect(jsonPath("$.roles[1]").value("COACH"));
        ArgumentCaptor<GrantRoleCommand> command = ArgumentCaptor.forClass(GrantRoleCommand.class);
        verify(grantRoleUseCase).execute(command.capture());
        assertThat(command.getValue().role()).isEqualTo(MemberRole.COACH);
        assertThat(command.getValue().memberId()).isEqualTo(target.id());
    }

    @Test
    void revokingARoleUsesDeleteOnTheSameResource() throws Exception {
        // Arrange
        when(revokeRoleUseCase.execute(any())).thenReturn(target);

        // Act
        var result = authenticated(HttpMethod.DELETE, "/api/v1/members/" + target.id() + "/roles/ADMIN");

        // Assert
        result.andExpect(status().isOk());
        ArgumentCaptor<RevokeRoleCommand> command = ArgumentCaptor.forClass(RevokeRoleCommand.class);
        verify(revokeRoleUseCase).execute(command.capture());
        assertThat(command.getValue().role()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    void anUnknownRoleIs400BeforeAnyUseCaseRuns() throws Exception {
        // Arrange
        String path = "/api/v1/members/" + target.id() + "/roles/SUPERUSER";

        // Act
        var grant = authenticated(HttpMethod.PUT, path);
        var revoke = authenticated(HttpMethod.DELETE, path);

        // Assert
        grant.andExpect(status().isBadRequest());
        revoke.andExpect(status().isBadRequest());
        verifyNoInteractions(grantRoleUseCase, revokeRoleUseCase);
    }

    @Test
    void deactivatingReportsTheCancelledBookingsAndTheSessionsToRetry() throws Exception {
        // Arrange
        SessionId failed = SessionId.generate();
        when(deactivateMemberUseCase.execute(any())).thenReturn(new MemberDeactivated(target.deactivate(), 2, List.of(failed)));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/members/" + target.id() + "/deactivation");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.member.status").value("INACTIVE"))
                .andExpect(jsonPath("$.bookingsCancelled").value(2))
                .andExpect(jsonPath("$.failedSessionIds[0]").value(failed.value().toString()));
        ArgumentCaptor<DeactivateMemberCommand> command = ArgumentCaptor.forClass(DeactivateMemberCommand.class);
        verify(deactivateMemberUseCase).execute(command.capture());
        assertThat(command.getValue().memberId()).isEqualTo(target.id());
    }

    @Test
    void assigningAPlanCreatesTheSubscriptionAndAnswers201() throws Exception {
        // Arrange
        Plan plan = WebFixtures.pack(other);
        Subscription subscription = WebFixtures.subscription(plan, target.id());
        when(assignPlanUseCase.execute(any())).thenReturn(subscription);

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/members/" + target.id() + "/subscriptions",
                "{\"planId\":\"" + plan.id() + "\",\"startDate\":\"2026-10-12\"}");

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.memberId").value(target.id().value().toString()))
                .andExpect(jsonPath("$.planId").value(plan.id().value().toString()))
                .andExpect(jsonPath("$.type").value("PACK"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.priceCents").value(4500));
        ArgumentCaptor<AssignPlanCommand> command = ArgumentCaptor.forClass(AssignPlanCommand.class);
        verify(assignPlanUseCase).execute(command.capture());
        assertThat(command.getValue().startDate()).isEqualTo(LocalDate.parse("2026-10-12"));
        assertThat(command.getValue().memberId()).isEqualTo(target.id());
        assertThat(command.getValue().planId()).isEqualTo(plan.id());
    }

    @Test
    void assigningWithoutAStartDateLetsTheUseCaseChoose() throws Exception {
        // Arrange
        Plan plan = WebFixtures.pack(other);
        when(assignPlanUseCase.execute(any())).thenReturn(WebFixtures.subscription(plan, target.id()));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/members/" + target.id() + "/subscriptions", "{\"planId\":\"" + plan.id() + "\"}");

        // Assert
        result.andExpect(status().isCreated());
        ArgumentCaptor<AssignPlanCommand> command = ArgumentCaptor.forClass(AssignPlanCommand.class);
        verify(assignPlanUseCase).execute(command.capture());
        assertThat(command.getValue().startDate()).isNull();
    }

    @Test
    void assigningNeedsAPlanAndAWellFormedDate() throws Exception {
        // Arrange
        String path = "/api/v1/members/" + target.id() + "/subscriptions";

        // Act
        var noPlan = authenticated(HttpMethod.POST, path, "{}");
        var badDate = authenticated(HttpMethod.POST, path, "{\"planId\":\"" + WebFixtures.pack(other).id() + "\",\"startDate\":\"12/10/2026\"}");
        var absurd = authenticated(HttpMethod.POST, path, "{\"planId\":\"" + WebFixtures.pack(other).id() + "\",\"startDate\":\"2101-01-01\"}");

        // Assert
        absurd.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("startDate"));
        noPlan.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("planId"));
        badDate.andExpect(status().isBadRequest());
        verifyNoInteractions(assignPlanUseCase);
    }

    @Test
    void resendingAnActivationLinkAnswers202ReceivedWithNothingElse() throws Exception {
        // Arrange
        String path = "/api/v1/members/" + target.id() + "/activation-links";

        // Act
        var result = authenticated(HttpMethod.POST, path);

        // Assert
        result.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(header().doesNotExist("Location"));
        ArgumentCaptor<ResendActivationLinkCommand> command = ArgumentCaptor.forClass(ResendActivationLinkCommand.class);
        verify(resendActivationLinkUseCase).execute(command.capture());
        assertThat(command.getValue().memberId()).isEqualTo(target.id());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void resendingWithAMemberIdThatIsNotAUuidIs400() throws Exception {
        // Arrange
        String path = "/api/v1/members/not-a-uuid/activation-links";

        // Act
        var result = authenticated(HttpMethod.POST, path);

        // Assert
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(resendActivationLinkUseCase);
    }

    @Test
    void aBodyIsIgnoredBecauseTheLinkAlwaysGoesToTheAccountsOwnAddress() throws Exception {
        // Arrange
        String path = "/api/v1/members/" + target.id() + "/activation-links";

        // Act
        var result = authenticated(HttpMethod.POST, path, "{\"email\":\"someone@example.com\"}");

        // Assert - the link goes to the account's address, never to one the caller names
        result.andExpect(status().isAccepted());
        ArgumentCaptor<ResendActivationLinkCommand> command = ArgumentCaptor.forClass(ResendActivationLinkCommand.class);
        verify(resendActivationLinkUseCase).execute(command.capture());
        assertThat(command.getValue().toString()).doesNotContain("someone@example.com");
    }

    @Test
    void resendingTooOftenIs429WithRetryAfter() throws Exception {
        // Arrange
        doThrow(new RateLimitExceededException(Duration.ofMinutes(20))).when(resendActivationLinkUseCase).execute(any());

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/members/" + target.id() + "/activation-links");

        // Assert
        result.andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1200"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }
}
