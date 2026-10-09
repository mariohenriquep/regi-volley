package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.LevelChange;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Creates and reconstitutes {@link Member}s: the only place a member is born. {@link Member}'s constructor checks every
 * invariant, so no path can produce an invalid member. Stateless, so its methods are static.
 *
 * <p>A new member always starts at the association's entry level (RN-20), which is why creation takes the
 * {@link Association}: the rule spans two aggregates and belongs to neither.
 */
public final class MemberFactory {

    private MemberFactory() {
    }

    /**
     * A new, ACTIVE member at the association's entry level (RN-20), with a generated id, at version 0. Used when
     * someone registers an association (roles {ADMIN}); a member who joins goes through
     * {@link #fromApprovedJoinRequest}.
     */
    public static Member create(Association association, ContactDetails contact, GdprConsent consent,
                                Set<MemberRole> roles, Clock clock) {
        Objects.requireNonNull(association, "association must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        return newMember(association, contact, consent, roles, clock.instant());
    }

    /**
     * The member an approved join request becomes (US-06): ACTIVE with the MEMBER role at the association's entry level
     * (RN-20), carrying the contact details and the consent the person gave, and joined at the instant of the decision.
     *
     * @param approved the request after {@code JoinRequest.approve}
     * @throws IllegalArgumentException if {@code association} isn't the request's, or the request is not APPROVED
     */
    public static Member fromApprovedJoinRequest(Association association, JoinRequest approved) {
        Objects.requireNonNull(association, "association must not be null");
        Objects.requireNonNull(approved, "approved must not be null");
        if (!association.id().equals(approved.associationId())) {
            throw new IllegalArgumentException("The join request belongs to another association");
        }
        if (approved.status() != JoinRequestStatus.APPROVED) {
            throw new IllegalArgumentException("Only an approved join request becomes a member");
        }
        Instant decidedAt = approved.decidedAt().orElseThrow();
        return newMember(association, approved.contact(), approved.consent(), Set.of(MemberRole.MEMBER), decidedAt);
    }

    /**
     * Rebuilds a member from persisted data.
     *
     * @param version the optimistic-lock version it was loaded with (architecture.md section 10)
     */
    public static Member reconstitute(MemberId id, AssociationId associationId, ContactDetails contact,
                                      GdprConsent consent, MemberStatus status, LevelId levelId, Set<MemberRole> roles,
                                      List<LevelChange> levelChanges, Instant joinedAt, Instant anonymisedAt,
                                      long version) {
        return new Member(id, associationId, contact, consent, status, levelId, roles, levelChanges, joinedAt,
                anonymisedAt, version);
    }

    private static Member newMember(Association association, ContactDetails contact, GdprConsent consent,
                                    Set<MemberRole> roles, Instant joinedAt) {
        return new Member(MemberId.generate(), association.id(), contact, consent, MemberStatus.ACTIVE,
                association.entryLevelId(), roles, List.of(), joinedAt, null, 0L);
    }
}
