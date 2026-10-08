package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.factory.MemberFactory;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.LevelChange;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.infrastructure.persistence.entity.MemberJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.MemberJpaEntity.LevelChangeRow;
import com.regivolley.api.infrastructure.persistence.entity.MemberJpaEntity.RoleRow;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Translates a {@link Member} to and from {@link MemberJpaEntity}. */
public final class MemberPersistenceMapper {

    private MemberPersistenceMapper() {
    }

    /** Rebuilds the aggregate through its factory, which re-checks its invariants. */
    public static Member toDomain(MemberJpaEntity entity) {
        ContactDetails contact = ContactDetails.reconstruct(entity.getName(), new EmailAddress(entity.getEmail()),
                entity.getPhone() == null ? null : new PhoneNumber(entity.getPhone()));
        Set<MemberRole> roles = entity.getRoles().stream()
                .map(row -> MemberRole.valueOf(row.getRole()))
                .collect(Collectors.toSet());
        List<LevelChange> history = entity.getLevelChanges().stream()
                .map(row -> new LevelChange(new LevelId(row.getFromLevelId()), new LevelId(row.getToLevelId()),
                        new MemberId(row.getChangedBy()), row.getChangedAt()))
                .toList();
        return MemberFactory.reconstitute(
                new MemberId(entity.getId()),
                new AssociationId(entity.getAssociationId()),
                contact,
                new GdprConsent(entity.getConsentGivenAt(), entity.getConsentPolicyVersion()),
                MemberStatus.valueOf(entity.getStatus()),
                new LevelId(entity.getLevelId()),
                roles,
                history,
                entity.getJoinedAt(),
                entity.getAnonymisedAt(),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    /** Copies the member onto {@code entity} (new, or loaded and managed). The version stays with the persistence layer. */
    public static void apply(Member member, MemberJpaEntity entity) {
        UUID associationId = member.associationId().value();
        entity.setId(member.id().value());
        entity.setAssociationId(associationId);
        entity.setName(member.contact().name());
        entity.setEmail(member.contact().email().value());
        entity.setPhone(member.contact().phone().map(PhoneNumber::value).orElse(null));
        entity.setConsentGivenAt(member.consent().givenAt());
        entity.setConsentPolicyVersion(member.consent().policyVersion());
        entity.setStatus(member.status().name());
        entity.setLevelId(member.levelId().value());
        entity.setJoinedAt(member.joinedAt());
        entity.setAnonymisedAt(member.anonymisedAt().orElse(null));
        CollectionSync.replace(entity.getRoles(), member.roles().stream()
                .map(role -> new RoleRow(associationId, role.name()))
                .collect(Collectors.toSet()));
        CollectionSync.replace(entity.getLevelChanges(), member.levelChanges().stream()
                .map(change -> new LevelChangeRow(associationId, change.from().value(), change.to().value(),
                        change.changedBy().value(), change.changedAt()))
                .toList());
    }
}
