package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.ChangeMemberLevelCommand;
import com.regivolley.api.application.command.DeactivateMemberCommand;
import com.regivolley.api.application.command.GetMemberDetailQuery;
import com.regivolley.api.application.command.GrantRoleCommand;
import com.regivolley.api.application.command.ListMembersQuery;
import com.regivolley.api.application.command.ResendActivationLinkCommand;
import com.regivolley.api.application.command.RevokeRoleCommand;
import com.regivolley.api.application.result.LevelChangeEntry;
import com.regivolley.api.application.result.MemberDeactivated;
import com.regivolley.api.application.result.MemberDetail;
import com.regivolley.api.application.result.MemberListEntry;
import com.regivolley.api.application.result.MemberListing;
import com.regivolley.api.application.result.MemberSubscriptionEntry;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.infrastructure.web.dto.LevelIdRequest;
import com.regivolley.api.infrastructure.web.dto.MemberDeactivationResponse;
import com.regivolley.api.infrastructure.web.dto.MemberDetailResponse;
import com.regivolley.api.infrastructure.web.dto.MemberListEntryResponse;
import com.regivolley.api.infrastructure.web.dto.MemberListResponse;
import com.regivolley.api.infrastructure.web.dto.MemberResponse;
import com.regivolley.api.infrastructure.web.dto.MemberRoleName;
import com.regivolley.api.infrastructure.web.dto.MemberStatusName;

import java.util.Comparator;
import java.util.List;
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

    public static ListMembersQuery listQuery(Actor actor, MemberStatusName status, String text) {
        return new ListMembersQuery(actor, status == null ? null : toDomain(status), text);
    }

    public static GetMemberDetailQuery detailQuery(Actor actor, UUID memberId) {
        return new GetMemberDetailQuery(actor, MemberId.of(memberId));
    }

    public static MemberListResponse toResponse(MemberListing listing) {
        return new MemberListResponse(listing.members().stream().map(MemberWebMapper::toResponse).toList(), listing.truncated());
    }

    private static MemberListEntryResponse toResponse(MemberListEntry entry) {
        Member member = entry.member();
        return new MemberListEntryResponse(member.id().value(), member.name(), member.email().value(),
                member.phone().map(PhoneNumber::value).orElse(null), member.levelId().value(), entry.levelName().orElse(null),
                roles(member), member.status().name(), entry.accountActivated(), member.joinedAt());
    }

    /** An anonymised member shows the placeholder name and no email or phone: nothing left of the person is handed out. */
    public static MemberDetailResponse toResponse(MemberDetail detail) {
        Member member = detail.member();
        boolean anonymised = member.isAnonymised();
        return new MemberDetailResponse(member.id().value(), anonymised, member.name(),
                anonymised ? null : member.email().value(), member.phone().map(PhoneNumber::value).orElse(null),
                member.levelId().value(), detail.levelName().orElse(null), roles(member), member.status().name(),
                detail.accountActivated(), member.joinedAt(), detail.levelChanges().stream().map(MemberWebMapper::toView).toList(),
                detail.subscriptions().stream().map(MemberWebMapper::toView).toList(), detail.subscriptionsTruncated());
    }

    private static MemberDetailResponse.LevelChangeView toView(LevelChangeEntry entry) {
        return new MemberDetailResponse.LevelChangeView(entry.change().from().value(), entry.fromName().orElse(null),
                entry.change().to().value(), entry.toName().orElse(null), entry.change().changedBy().value(),
                entry.changedByName().orElse(null), entry.change().changedAt());
    }

    private static MemberDetailResponse.MemberSubscriptionView toView(MemberSubscriptionEntry entry) {
        Subscription subscription = entry.subscription();
        return new MemberDetailResponse.MemberSubscriptionView(subscription.id().value(), subscription.planId().value(),
                entry.planName().orElse(null), subscription.type().name(), subscription.startDate(), subscription.endDate(),
                subscription.paymentStatus().name(), subscription.price().cents(), entry.paid().cents(), entry.outstanding().cents(),
                subscription.creditsUsed(), subscription.creditsLeft().isPresent() ? subscription.creditsLeft().getAsInt() : null);
    }

    private static List<MemberRoleName> roles(Member member) {
        return member.roles().stream().sorted(Comparator.naturalOrder()).map(MemberWebMapper::toWire).toList();
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

    /** Every wire spelling has its domain status; a new value on either side stops the build here. */
    static MemberStatus toDomain(MemberStatusName name) {
        return switch (name) {
            case ACTIVE -> MemberStatus.ACTIVE;
            case INACTIVE -> MemberStatus.INACTIVE;
        };
    }

    /** The wire spelling of a role; a new value on either side stops the build here. */
    static MemberRoleName toWire(MemberRole role) {
        return switch (role) {
            case MEMBER -> MemberRoleName.MEMBER;
            case COACH -> MemberRoleName.COACH;
            case ADMIN -> MemberRoleName.ADMIN;
        };
    }
}
