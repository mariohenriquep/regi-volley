-- Plan aggregate (US-19, RN-13, RN-14) and Subscription aggregate (US-20, RN-15, RN-16, RN-18).
-- Money is whole cents (bigint). Dates are Europe/Lisbon calendar dates, not instants.
-- Optimistic locking: version on plans and subscriptions (architecture.md section 10); for subscriptions it
-- stops two concurrent bookings of one member from spending the same last credit.

CREATE TABLE plans (
    id               uuid        PRIMARY KEY,
    association_id   uuid        NOT NULL REFERENCES associations (id),
    name             text        NOT NULL,
    plan_type        varchar(30) NOT NULL,
    sessions_per_week integer,
    credits          integer,
    price_cents      bigint      NOT NULL,
    validity_days    integer,
    version          bigint      NOT NULL,
    CONSTRAINT uq_plans_association_id_id UNIQUE (association_id, id),
    CONSTRAINT ck_plans_type CHECK (plan_type IN ('MONTHLY_UNLIMITED', 'MONTHLY_N_PER_WEEK', 'PACK', 'SINGLE_SESSION')),
    CONSTRAINT ck_plans_price CHECK (price_cents >= 0),
    CONSTRAINT ck_plans_sessions_per_week CHECK (sessions_per_week IS NULL OR sessions_per_week >= 1),
    CONSTRAINT ck_plans_credits CHECK (credits IS NULL OR credits >= 1),
    CONSTRAINT ck_plans_validity CHECK (validity_days IS NULL OR validity_days >= 1),
    CONSTRAINT ck_plans_version CHECK (version >= 0)
);

CREATE TABLE plan_allowed_levels (
    association_id uuid NOT NULL,
    plan_id        uuid NOT NULL,
    level_id       uuid NOT NULL,
    PRIMARY KEY (plan_id, level_id),
    FOREIGN KEY (association_id, plan_id) REFERENCES plans (association_id, id)
);

CREATE TABLE subscriptions (
    id                uuid        PRIMARY KEY,
    association_id    uuid        NOT NULL REFERENCES associations (id),
    member_id         uuid        NOT NULL,
    plan_id           uuid        NOT NULL,
    plan_type         varchar(30) NOT NULL,
    sessions_per_week integer,
    credits           integer,
    start_date        date        NOT NULL,
    end_date          date        NOT NULL,
    payment_status    varchar(20) NOT NULL,
    version           bigint      NOT NULL,
    CONSTRAINT uq_subscriptions_association_id_id UNIQUE (association_id, id),
    CONSTRAINT ck_subscriptions_type CHECK (plan_type IN ('MONTHLY_UNLIMITED', 'MONTHLY_N_PER_WEEK', 'PACK', 'SINGLE_SESSION')),
    CONSTRAINT ck_subscriptions_period CHECK (end_date >= start_date),
    CONSTRAINT ck_subscriptions_payment_status CHECK (payment_status IN ('PENDING', 'PAID', 'OVERDUE')),
    CONSTRAINT ck_subscriptions_version CHECK (version >= 0)
);

CREATE INDEX ix_subscriptions_association_member ON subscriptions (association_id, member_id, start_date);

-- Snapshot of the plan's allowed levels taken when the subscription was created.
CREATE TABLE subscription_allowed_levels (
    association_id  uuid NOT NULL,
    subscription_id uuid NOT NULL,
    level_id        uuid NOT NULL,
    PRIMARY KEY (subscription_id, level_id),
    FOREIGN KEY (association_id, subscription_id) REFERENCES subscriptions (association_id, id)
);

-- One row per booking that currently holds a place; position keeps the order they were charged.
CREATE TABLE subscription_credit_usages (
    association_id  uuid    NOT NULL,
    subscription_id uuid    NOT NULL,
    position        integer NOT NULL,
    booking_id      uuid    NOT NULL,
    session_date    date    NOT NULL,
    PRIMARY KEY (subscription_id, position),
    FOREIGN KEY (association_id, subscription_id) REFERENCES subscriptions (association_id, id)
);
