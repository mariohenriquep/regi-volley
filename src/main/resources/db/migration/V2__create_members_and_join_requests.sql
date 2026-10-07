-- Member aggregate (US-05..US-08) and JoinRequest aggregate (US-05, US-06). Personal data lives
-- only in name/email/phone; erasure overwrites them with placeholders (RGPD).
-- Optimistic locking: version on both (architecture.md section 10), so a stale copy can never undo an
-- erasure and two decisions on one request cannot both be stored.

CREATE TABLE members (
    id                     uuid         PRIMARY KEY,
    association_id         uuid         NOT NULL REFERENCES associations (id),
    name                   varchar(100) NOT NULL,
    email                  varchar(254) NOT NULL,
    phone                  varchar(16),
    consent_given_at       timestamptz  NOT NULL,
    consent_policy_version varchar(50)  NOT NULL,
    status                 varchar(20)  NOT NULL,
    level_id               uuid         NOT NULL,
    joined_at              timestamptz  NOT NULL,
    anonymised_at          timestamptz,
    version                bigint       NOT NULL,
    CONSTRAINT uq_members_association_id_id UNIQUE (association_id, id),
    -- Anonymised placeholder emails are unique per member, so this holds for erased members too.
    CONSTRAINT uq_members_association_email UNIQUE (association_id, email),
    CONSTRAINT ck_members_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_members_version CHECK (version >= 0)
);

CREATE TABLE member_roles (
    association_id uuid        NOT NULL,
    member_id      uuid        NOT NULL,
    role           varchar(20) NOT NULL,
    PRIMARY KEY (member_id, role),
    FOREIGN KEY (association_id, member_id) REFERENCES members (association_id, id),
    CONSTRAINT ck_member_roles_role CHECK (role IN ('MEMBER', 'COACH', 'ADMIN'))
);

CREATE TABLE member_level_changes (
    association_id uuid        NOT NULL,
    member_id      uuid        NOT NULL,
    position       integer     NOT NULL,
    from_level_id  uuid        NOT NULL,
    to_level_id    uuid        NOT NULL,
    changed_by     uuid        NOT NULL,
    changed_at     timestamptz NOT NULL,
    PRIMARY KEY (member_id, position),
    FOREIGN KEY (association_id, member_id) REFERENCES members (association_id, id),
    CONSTRAINT ck_member_level_changes_moves CHECK (from_level_id <> to_level_id)
);

CREATE TABLE join_requests (
    id                     uuid         PRIMARY KEY,
    association_id         uuid         NOT NULL REFERENCES associations (id),
    name                   varchar(100) NOT NULL,
    email                  varchar(254) NOT NULL,
    phone                  varchar(16),
    consent_given_at       timestamptz  NOT NULL,
    consent_policy_version varchar(50)  NOT NULL,
    status                 varchar(20)  NOT NULL,
    requested_at           timestamptz  NOT NULL,
    decided_at             timestamptz,
    decided_by             uuid,
    rejection_reason       varchar(500),
    anonymised_at          timestamptz,
    version                bigint       NOT NULL,
    CONSTRAINT ck_join_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_join_requests_version CHECK (version >= 0)
);

CREATE INDEX ix_join_requests_association_status ON join_requests (association_id, status, requested_at);
