-- Association aggregate (the tenant) and its levels (US-01, US-03, RN-20).
-- Optimistic locking: associations.version (architecture.md section 10).

CREATE TABLE associations (
    id                       uuid         PRIMARY KEY,
    name                     varchar(100) NOT NULL,
    short_name               varchar(40)  NOT NULL,
    nif                      varchar(9),
    locality                 varchar(100) NOT NULL,
    contact_email            varchar(254) NOT NULL,
    booking_window_days      integer      NOT NULL,
    free_cancellation_hours  integer      NOT NULL,
    session_generation_weeks integer      NOT NULL DEFAULT 4,
    entry_level_id           uuid         NOT NULL,
    version                  bigint       NOT NULL,
    CONSTRAINT uq_associations_short_name UNIQUE (short_name),
    CONSTRAINT ck_associations_booking_window CHECK (booking_window_days >= 1),
    CONSTRAINT ck_associations_free_cancellation CHECK (free_cancellation_hours >= 0),
    CONSTRAINT ck_associations_generation_weeks CHECK (session_generation_weeks BETWEEN 1 AND 12),
    CONSTRAINT ck_associations_version CHECK (version >= 0)
);

CREATE TABLE levels (
    id             uuid        PRIMARY KEY,
    association_id uuid        NOT NULL REFERENCES associations (id),
    name           varchar(50) NOT NULL,
    level_rank     integer     NOT NULL,
    CONSTRAINT uq_levels_association_id_id UNIQUE (association_id, id),
    CONSTRAINT ck_levels_rank CHECK (level_rank >= 0)
);

CREATE INDEX ix_levels_association_rank ON levels (association_id, level_rank);

-- The entry level (RN-20) must be one of the association's own levels. Deferred because the
-- association row is written before its levels.
ALTER TABLE associations
    ADD CONSTRAINT fk_associations_entry_level
        FOREIGN KEY (id, entry_level_id) REFERENCES levels (association_id, id)
            DEFERRABLE INITIALLY DEFERRED;
