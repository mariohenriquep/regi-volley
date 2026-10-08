-- Credentials and sessions (issue #31, threat model D-6..D-11): who can sign in, as which member, and the
-- single-use secrets around it. Nothing here stores a secret in clear: passwords are Argon2id hashes, refresh tokens and
-- emailed link tokens are SHA-256 hashes of 256-bit random values, the security stamp is 256 bits of randomness that is
-- never derived from the password.

-- An account is a PERSON's identity, not tenant business data, and one person may later belong to several associations
-- (open question in requirements.md). It therefore has NO association_id: this is the one documented exception to
-- "every business table has association_id" (architecture.md section 8). All tenant data hangs off membership below; the
-- credential tables that follow (refresh_token, email_link) are reached from the account or from a membership and carry
-- no tenant column of their own either.
CREATE TABLE app_user (
    id             uuid         PRIMARY KEY,
    -- Normalised (trimmed, lower case) by the application; the check keeps every writer to the same form.
    email          varchar(254) NOT NULL,
    status         varchar(20)  NOT NULL,
    -- NULL until the account is activated from the emailed link: nobody can sign in without proving the mailbox (L3).
    password_hash  varchar(255),
    -- 256 random bits, base64url (43 characters). Rotated on password change, reset and logout-all: an access token
    -- carries it ("sv") and dies the moment it changes.
    security_stamp varchar(64)  NOT NULL,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email_normalised CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- The link between an account and a member of one association. PENDING until the activation link is consumed (L4: nobody
-- can attach a membership to someone else's account). The composite foreign key keeps member_id inside its tenant, like
-- payments do, so a membership can never point at another association's member.
CREATE TABLE membership (
    id             uuid        PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    association_id uuid        NOT NULL,
    member_id      uuid        NOT NULL,
    status         varchar(20) NOT NULL,
    created_at     timestamptz NOT NULL,
    confirmed_at   timestamptz,
    FOREIGN KEY (association_id, member_id) REFERENCES members (association_id, id),
    CONSTRAINT uq_membership_association_member UNIQUE (association_id, member_id),
    CONSTRAINT uq_membership_user_association UNIQUE (user_id, association_id),
    -- Lets the credential tables below point at a membership AND the user it belongs to, so a token or link can never name
    -- one user together with another user's membership.
    CONSTRAINT uq_membership_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_membership_status CHECK (status IN ('PENDING', 'CONFIRMED')),
    CONSTRAINT ck_membership_confirmed CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))
);

CREATE INDEX ix_membership_association ON membership (association_id);

-- Refresh tokens (D-7): opaque, only the hash is stored, rotated on every use. A family is one login; a token whose
-- successor exists has used_at set. expires_at is the family's absolute end (90 days from the login, copied to every
-- successor); idle_expires_at moves 30 days forward with every rotation.
CREATE TABLE refresh_token (
    id              uuid         PRIMARY KEY,
    family_id       uuid         NOT NULL,
    user_id         uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    membership_id   uuid         NOT NULL,
    token_hash      varchar(64)  NOT NULL,
    parent_id       uuid         REFERENCES refresh_token (id) ON DELETE SET NULL,
    issued_at       timestamptz  NOT NULL,
    expires_at      timestamptz  NOT NULL,
    idle_expires_at timestamptz  NOT NULL,
    used_at         timestamptz,
    revoked_at      timestamptz,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    -- the membership is the user's own
    CONSTRAINT fk_refresh_token_membership_user FOREIGN KEY (membership_id, user_id)
        REFERENCES membership (id, user_id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);

-- Emailed single-use links (D-11): activation (confirms the membership and sets the first password) and password reset.
-- Only the hash of the token is stored; consumed_at is set when the link is used or superseded by a newer one.
CREATE TABLE email_link (
    id            uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Set for ACTIVATION (the membership it confirms), NULL for PASSWORD_RESET.
    membership_id uuid,
    purpose       varchar(20) NOT NULL,
    token_hash    varchar(64) NOT NULL,
    created_at    timestamptz NOT NULL,
    expires_at    timestamptz NOT NULL,
    consumed_at   timestamptz,
    CONSTRAINT uq_email_link_hash UNIQUE (token_hash),
    -- the membership is the user's own (not checked while membership_id is NULL, i.e. for a reset link)
    CONSTRAINT fk_email_link_membership_user FOREIGN KEY (membership_id, user_id)
        REFERENCES membership (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_email_link_purpose CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_email_link_membership CHECK ((purpose = 'ACTIVATION') = (membership_id IS NOT NULL))
);

CREATE INDEX ix_email_link_user ON email_link (user_id);
CREATE INDEX ix_email_link_membership ON email_link (membership_id);

-- At most one LIVE link per membership (activation) and one live reset per user: a newer link supersedes the older ones in the same
-- transaction, and two links issued at the same instant cannot both stay open - the loser repeats (LinkAlreadyIssuedException).
CREATE UNIQUE INDEX uq_email_link_open_activation ON email_link (membership_id)
    WHERE purpose = 'ACTIVATION' AND consumed_at IS NULL;
CREATE UNIQUE INDEX uq_email_link_open_reset ON email_link (user_id)
    WHERE purpose = 'PASSWORD_RESET' AND consumed_at IS NULL;
