package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.AssignPlanUseCase;
import com.regivolley.api.application.usecase.ChangeMemberLevelUseCase;
import com.regivolley.api.application.usecase.DeactivateMemberUseCase;
import com.regivolley.api.application.usecase.GrantRoleUseCase;
import com.regivolley.api.application.usecase.RevokeRoleUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.AssignPlanRequest;
import com.regivolley.api.infrastructure.web.dto.LevelIdRequest;
import com.regivolley.api.infrastructure.web.dto.MemberDeactivationResponse;
import com.regivolley.api.infrastructure.web.dto.MemberResponse;
import com.regivolley.api.infrastructure.web.dto.MemberRoleName;
import com.regivolley.api.infrastructure.web.dto.SubscriptionResponse;
import com.regivolley.api.infrastructure.web.mapper.MemberWebMapper;
import com.regivolley.api.infrastructure.web.mapper.SubscriptionWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * What an administrator does to a member (US-04, US-07, US-08, US-20); the use cases check the role. The member in the path is only
 * ever the target, resolved inside the caller's association, so another association's member is a 404.
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberAdminController {

    private final ChangeMemberLevelUseCase changeLevel;
    private final GrantRoleUseCase grantRole;
    private final RevokeRoleUseCase revokeRole;
    private final DeactivateMemberUseCase deactivate;
    private final AssignPlanUseCase assignPlan;

    public MemberAdminController(ChangeMemberLevelUseCase changeLevel, GrantRoleUseCase grantRole, RevokeRoleUseCase revokeRole,
                                 DeactivateMemberUseCase deactivate, AssignPlanUseCase assignPlan) {
        this.changeLevel = changeLevel;
        this.grantRole = grantRole;
        this.revokeRole = revokeRole;
        this.deactivate = deactivate;
        this.assignPlan = assignPlan;
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
}
