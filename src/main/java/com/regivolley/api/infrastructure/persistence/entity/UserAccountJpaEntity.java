package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A login account (table {@code app_user}). An identity, not tenant data: it has no association (architecture.md section 8).
 *
 * Persistence model only: translated to and from {@code UserAccount} by its {@code UserAccountPersistenceMapper}; never exposed
 * outside {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "app_user")
public class UserAccountJpaEntity {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "security_stamp", nullable = false)
    private String securityStamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserAccountJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSecurityStamp() {
        return securityStamp;
    }

    public void setSecurityStamp(String securityStamp) {
        this.securityStamp = securityStamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
