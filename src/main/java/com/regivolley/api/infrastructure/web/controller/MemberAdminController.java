package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.AssignPlanUseCase;
import com.regivolley.api.application.usecase.ChangeMemberLevelUseCase;
import com.regivolley.api.application.usecase.DeactivateMemberUseCase;
import com.regivolley.api.application.usecase.GetMemberDetailUseCase;
import com.regivolley.api.application.usecase.GrantRoleUseCase;
import com.regivolley.api.application.usecase.ListMembersUseCase;
import com.regivolley.api.application.usecase.ResendActivationLinkUseCase;
import com.regivolley.api.application.usecase.RevokeRoleUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.AcknowledgementResponse;
import com.regivolley.api.infrastructure.web.dto.AssignPlanRequest;
import com.regivolley.api.infrastructure.web.dto.LevelIdRequest;
import com.regivolley.api.infrastructure.web.dto.MemberDeactivationResponse;
import com.regivolley.api.infrastructure.web.dto.MemberDetailResponse;
import com.regivolley.api.infrastructure.web.dto.MemberListResponse;
import com.regivolley.api.infrastructure.web.dto.MemberResponse;
import com.regivolley.api.infrastructure.web.dto.MemberRoleName;
import com.regivolley.api.infrastructure.web.dto.MemberStatusName;
import com.regivolley.api.infrastructure.web.dto.SubscriptionResponse;
import com.regivolley.api.infrastructure.web.mapper.MemberWebMapper;
import com.regivolley.api.infrastructure.web.mapper.SubscriptionWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * What an administrator reads about the members (US-07, US-08: the list; US-04, US-20: one member), does to a member (US-04, US-07, US-08, US-20) and re-sends an activation link (P7); the use cases check the role. The member in the path is only
 * ever the target, resolved inside the caller's association, so another association's member is a 404.
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberAdminController {

    private final ListMembersUseCase listMembers;
    private final GetMemberDetailUseCase memberDetail;
    private final ChangeMemberLevelUseCase changeLevel;
    private final GrantRoleUseCase grantRole;
    private final RevokeRoleUseCase revokeRole;
    private final DeactivateMemberUseCase deactivate;
    private final AssignPlanUseCase assignPlan;
    private final ResendActivationLinkUseCase resendActivationLink;

    public MemberAdminController(ListMembersUseCase listMembers, GetMemberDetailUseCase memberDetail, ChangeMemberLevelUseCase changeLevel, GrantRoleUseCase grantRole, RevokeRoleUseCase revokeRole,
                                 DeactivateMemberUseCase deactivate, AssignPlanUseCase assignPlan,
                                 ResendActivationLinkUseCase resendActivationLink) {
        this.listMembers = listMembers;
        this.memberDetail = memberDetail;
        this.changeLevel = changeLevel;
        this.grantRole = grantRole;
        this.revokeRole = revokeRole;
        this.deactivate = deactivate;
        this.assignPlan = assignPlan;
        this.resendActivationLink = resendActivationLink;
    }

    /**
     * The association's members by name, at most 300 ({@code truncated} says when there were more). {@code status} narrows by
     * {@code ACTIVE} / {@code INACTIVE}; {@code q} keeps those whose name or email contains it, ignoring case. Anonymised members are not listed.
     */
    @GetMapping
    public MemberListResponse list(@CurrentActor AuthenticatedActor caller, @RequestParam(required = false) MemberStatusName status,
                                   @RequestParam(required = false) String q) {
        return MemberWebMapper.toResponse(listMembers.execute(MemberWebMapper.listQuery(caller.actor(), status, q)));
    }

    /** One member of the caller's association (another association's member is a 404): contact data, level history and subscriptions. */
    @GetMapping("/{memberId}")
    public MemberDetailResponse detail(@CurrentActor AuthenticatedActor caller, @PathVariable UUID memberId) {
        return MemberWebMapper.toResponse(memberDetail.execute(MemberWebMapper.detailQuery(caller.actor(), memberId)));
    }

    @PutMapping("/{memberId}/level")
    public MemberResponse level(@CurrentActor AuthenticatedActor caller, @PathVariable UUID memberId,
                                @Valid @RequestBody LevelIdRequest body) {
        return MemberWebMapper.toResponse(changeLevel.execute(MemberWebMapper.changeLevelCommand(caller.actor(), memberId, body)));
    }

    @PutMapping("/{memberId}/roles/{role}")
    public MemberResponse grant(@CurrentActor AuthenticatedActor caller, @PathVariable UUID memberId, @PathVariable MemberRoleName role) {
        return MemberWebMapper.toResponse(grantRole.execute(MemberWebMapper.grantRoleCommand(caller.actor(), memberId, role)));
    }

    @DeleteMapping("/{memberId}/roles/{role}")
    public MemberResponse revoke(@CurrentActor AuthenticatedActor caller, @PathVariable UUID memberId, @PathVariable MemberRoleName role) {
        return MemberWebMapper.toResponse(revokeRole.execute(MemberWebMapper.revokeRoleCommand(caller.actor(), memberId, role)));
    }

    @PostMapping("/{memberId}/deactivation")
    public MemberDeactivationResponse deactivate(@CurrentActor AuthenticatedActor caller, @PathVariable UUID memberId) {
        return MemberWebMapper.toResponse(deactivate.execute(MemberWebMapper.deactivateCommand(caller.actor(), memberId)));
    }

    @PostMapping("/{memberId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse assign(@CurrentActor AuthenticatedActor caller, @PathVariable UUID memberId,
                                       @Valid @RequestBody AssignPlanRequest body) {
        return SubscriptionWebMapper.toResponse(assignPlan.execute(SubscriptionWebMapper.assignCommand(caller.actor(), memberId, body)));
    }

    /**
     * Sends the member's activation link again (threat model P7). Always {@code 202 {"status":"RECEIVED"}}, whether or not a link went out: the
     * work is done off the request and the answer must not tell whether the member has already activated. A body, if any, is ignored: the
     * link goes to the account's own address, never to one the caller names.
     */
    @PostMapping("/{memberId}/activation-links")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcknowledgementResponse resendActivationLink(@CurrentActor AuthenticatedActor caller, @PathVariable UUID memberId) {
        resendActivationLink.execute(MemberWebMapper.resendActivationCommand(caller.actor(), memberId));
        return AcknowledgementResponse.received();
    }
}
