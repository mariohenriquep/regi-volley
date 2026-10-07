package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A member of an association (table {@code members}); name, email and phone are personal data and are never logged.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code MemberPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "members")
public class MemberJpaEntity {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    @Column(name = "consent_policy_version", nullable = false)
    private String consentPolicyVersion;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "level_id", nullable = false)
    private UUID levelId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "anonymised_at")
    private Instant anonymisedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @ElementCollection
    @CollectionTable(name = "member_roles", joinColumns = @JoinColumn(name = "member_id"))
    private Set<RoleRow> roles = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "member_level_changes", joinColumns = @JoinColumn(name = "member_id"))
    @OrderColumn(name = "position")
    private List<LevelChangeRow> levelChanges = new ArrayList<>();

    public MemberJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public void setAssociationId(UUID associationId) {
        this.associationId = associationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Instant getConsentGivenAt() {
        return consentGivenAt;
    }

    public void setConsentGivenAt(Instant consentGivenAt) {
        this.consentGivenAt = consentGivenAt;
    }

    public String getConsentPolicyVersion() {
        return consentPolicyVersion;
    }

    public void setConsentPolicyVersion(String consentPolicyVersion) {
        this.consentPolicyVersion = consentPolicyVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getLevelId() {
        return levelId;
    }

    public void setLevelId(UUID levelId) {
        this.levelId = levelId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getAnonymisedAt() {
        return anonymisedAt;
    }

    public void setAnonymisedAt(Instant anonymisedAt) {
        this.anonymisedAt = anonymisedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Set<RoleRow> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleRow> roles) {
        this.roles = roles;
    }

    public List<LevelChangeRow> getLevelChanges() {
        return levelChanges;
    }

    public void setLevelChanges(List<LevelChangeRow> levelChanges) {
        this.levelChanges = levelChanges;
    }

    /** One role held (table {@code member_roles}). */
    @Embeddable
    public static class RoleRow {

        @Column(name = "association_id", nullable = false)
        private UUID associationId;

        @Column(name = "role", nullable = false)
        private String role;

        public RoleRow() {
        }

        public RoleRow(UUID associationId, String role) {
            this.associationId = associationId;
            this.role = role;
        }

        public UUID getAssociationId() {
            return associationId;
        }

        public String getRole() {
            return role;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RoleRow other)) return false;
            return Objects.equals(associationId, other.associationId) && Objects.equals(role, other.role);
        }

        @Override
        public int hashCode() {
            return Objects.hash(associationId, role);
        }
    }

    /** One level history entry (table {@code member_level_changes}), oldest first. */
    @Embeddable
    public static class LevelChangeRow {

        @Column(name = "association_id", nullable = false)
        private UUID associationId;

        @Column(name = "from_level_id", nullable = false)
        private UUID fromLevelId;

        @Column(name = "to_level_id", nullable = false)
        private UUID toLevelId;

        @Column(name = "changed_by", nullable = false)
        private UUID changedBy;

        @Column(name = "changed_at", nullable = false)
        private Instant changedAt;

        public LevelChangeRow() {
        }

        public LevelChangeRow(UUID associationId, UUID fromLevelId, UUID toLevelId, UUID changedBy, Instant changedAt) {
            this.associationId = associationId;
            this.fromLevelId = fromLevelId;
            this.toLevelId = toLevelId;
            this.changedBy = changedBy;
            this.changedAt = changedAt;
        }

        public UUID getAssociationId() {
            return associationId;
        }

        public UUID getFromLevelId() {
            return fromLevelId;
        }

        public UUID getToLevelId() {
            return toLevelId;
        }

        public UUID getChangedBy() {
            return changedBy;
        }

        public Instant getChangedAt() {
            return changedAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LevelChangeRow other)) return false;
            return Objects.equals(associationId, other.associationId) && Objects.equals(fromLevelId, other.fromLevelId) && Objects.equals(toLevelId, other.toLevelId) && Objects.equals(changedBy, other.changedBy) && Objects.equals(changedAt, other.changedAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(associationId, fromLevelId, toLevelId, changedBy, changedAt);
        }
    }
}
