package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.ChangeMemberLevelCommand;
import com.regivolley.api.application.command.DeactivateMemberCommand;
import com.regivolley.api.application.command.GrantRoleCommand;
import com.regivolley.api.application.command.ResendActivationLinkCommand;
import com.regivolley.api.application.command.RevokeRoleCommand;
import com.regivolley.api.application.result.MemberDeactivated;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.infrastructure.web.dto.LevelIdRequest;
import com.regivolley.api.infrastructure.web.dto.MemberDeactivationResponse;
import com.regivolley.api.infrastructure.web.dto.MemberResponse;
import com.regivolley.api.infrastructure.web.dto.MemberRoleName;

import java.util.Comparator;
import java.util.UUID;

/** Member administration (US-04, US-07, US-08): the member in the path is only ever the target of the operation. */
public final class MemberWebMapper {

    private MemberWebMapper() {
    }

    public static ChangeMemberLevelCommand changeLevelCommand(Actor actor, UUID memberId, LevelIdRequest body) {
        return new ChangeMemberLevelCommand(actor, MemberId.of(memberId), LevelId.of(body.levelId()));
    }

    public static GrantRoleCommand grantRoleCommand(Actor actor, UUID memberId, MemberRoleName role) {
        return new GrantRoleCommand(actor, MemberId.of(memberId), toDomain(role));
    }

    public static RevokeRoleCommand revokeRoleCommand(Actor actor, UUID memberId, MemberRoleName role) {
        return new RevokeRoleCommand(actor, MemberId.of(memberId), toDomain(role));
    }

    public static DeactivateMemberCommand deactivateCommand(Actor actor, UUID memberId) {
        return new DeactivateMemberCommand(actor, MemberId.of(memberId));
    }

    public static ResendActivationLinkCommand resendActivationCommand(Actor actor, UUID memberId) {
        return new ResendActivationLinkCommand(actor, MemberId.of(memberId));
    }

    public static MemberResponse toResponse(Member member) {
        return new MemberResponse(member.id().value(), member.name(), member.email().value(),
                member.phone().map(PhoneNumber::value).orElse(null), member.status().name(), member.levelId().value(),
                member.roles().stream().sorted(Comparator.naturalOrder()).map(MemberRole::name).toList(), member.joinedAt());
    }

    public static MemberDeactivationResponse toResponse(MemberDeactivated deactivated) {
        return new MemberDeactivationResponse(toResponse(deactivated.member()), deactivated.bookingsCancelled(),
                deactivated.failedSessions().stream().map(SessionId::value).toList());
    }

    /** Every wire spelling has its domain role; a new value on either side stops the build here. */
    static MemberRole toDomain(MemberRoleName name) {
        return switch (name) {
            case MEMBER -> MemberRole.MEMBER;
            case COACH -> MemberRole.COACH;
            case ADMIN -> MemberRole.ADMIN;
        };
    }
}
