package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.infrastructure.persistence.entity.MembershipJpaEntity;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;

/** Translates a {@link Membership} to and from {@link MembershipJpaEntity}. */
public final class MembershipPersistenceMapper {

    private MembershipPersistenceMapper() {
    }

    public static Membership toDomain(MembershipJpaEntity entity) {
        return new Membership(entity.getId(), entity.getUserId(), AssociationId.of(entity.getAssociationId()),
                MemberId.of(entity.getMemberId()), MembershipStatus.valueOf(entity.getStatus()), entity.getCreatedAt(),
                entity.getConfirmedAt());
    }

    public static MembershipJpaEntity toNewEntity(Membership membership) {
        MembershipJpaEntity entity = new MembershipJpaEntity();
        entity.setId(membership.id());
        entity.setUserId(membership.userId());
        entity.setAssociationId(membership.associationId().value());
        entity.setMemberId(membership.memberId().value());
        entity.setStatus(membership.status().name());
        entity.setCreatedAt(membership.createdAt());
        entity.setConfirmedAt(membership.confirmedAt());
        return entity;
    }
}
