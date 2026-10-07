-- Session aggregate with its bookings (US-10..US-14, RN-02..RN-12). Instants are timestamptz (UTC).
-- Concurrency (architecture.md section 10): sessions.version is the optimistic lock that protects
-- the last seat; the partial unique index on bookings and the unique generated start are the
-- database backstops.

CREATE TABLE sessions (
    id                  uuid        PRIMARY KEY,
    association_id      uuid        NOT NULL REFERENCES associations (id),
    training_group_id   uuid        NOT NULL,
    coach_id            uuid        NOT NULL,
    starts_at           timestamptz NOT NULL,
    ends_at             timestamptz NOT NULL,
    capacity            integer     NOT NULL,
    status              varchar(20) NOT NULL,
    cancellation_reason text,
    version             bigint      NOT NULL,
    CONSTRAINT uq_sessions_association_id_id UNIQUE (association_id, id),
    -- RN-01: re-running generation can never create the same occurrence twice, even concurrently.
    CONSTRAINT uq_sessions_group_starts_at UNIQUE (association_id, training_group_id, starts_at),
    CONSTRAINT ck_sessions_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_sessions_capacity CHECK (capacity > 0),
    CONSTRAINT ck_sessions_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_sessions_cancellation_reason CHECK ((status = 'CANCELLED') = (cancellation_reason IS NOT NULL)),
    CONSTRAINT ck_sessions_version CHECK (version >= 0)
);

-- Week view (US-13) and the RN-07 overlap check.
CREATE INDEX ix_sessions_association_starts_at ON sessions (association_id, starts_at);

CREATE TABLE bookings (
    id                uuid        PRIMARY KEY,
    association_id    uuid        NOT NULL,
    session_id        uuid        NOT NULL,
    member_id         uuid        NOT NULL,
    status            varchar(20) NOT NULL,
    requested_at      timestamptz NOT NULL,
    confirmed_at      timestamptz,
    cancellation_kind varchar(20),
    FOREIGN KEY (association_id, session_id) REFERENCES sessions (association_id, id),
    CONSTRAINT ck_bookings_status CHECK (status IN ('WAITLISTED', 'CONFIRMED', 'ATTENDED', 'NO_SHOW', 'CANCELLED')),
    CONSTRAINT ck_bookings_cancellation_kind CHECK (cancellation_kind IS NULL
        OR cancellation_kind IN ('FREE', 'LATE', 'BY_SESSION', 'NOT_PROMOTED')),
    CONSTRAINT ck_bookings_cancellation CHECK ((status = 'CANCELLED') = (cancellation_kind IS NOT NULL))
);

-- RN-07: one live booking per member per session; booking again after cancelling is allowed.
CREATE UNIQUE INDEX uq_bookings_live_member_session ON bookings (session_id, member_id) WHERE status <> 'CANCELLED';

-- "Which sessions does this member hold a place in" (RN-07 overlap) and the FIFO waitlist read.
CREATE INDEX ix_bookings_association_member ON bookings (association_id, member_id) WHERE status <> 'CANCELLED';
CREATE INDEX ix_bookings_session_requested ON bookings (session_id, requested_at, id);
