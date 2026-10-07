-- TrainingGroup aggregate (US-09, RN-01, RN-21). Schedule slots are Europe/Lisbon wall-clock
-- (day of week ISO 1..7, local time, minutes), never instants (architecture.md section 9).
-- venue_id has no table yet: venues are not modelled beyond their id.

CREATE TABLE training_groups (
    id               uuid         PRIMARY KEY,
    association_id   uuid         NOT NULL REFERENCES associations (id),
    name             varchar(100) NOT NULL,
    venue_id         uuid         NOT NULL,
    default_capacity integer      NOT NULL,
    coach_id         uuid         NOT NULL,
    status           varchar(20)  NOT NULL,
    version          bigint       NOT NULL,
    CONSTRAINT uq_training_groups_association_id_id UNIQUE (association_id, id),
    CONSTRAINT ck_training_groups_capacity CHECK (default_capacity > 0),
    CONSTRAINT ck_training_groups_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_training_groups_version CHECK (version >= 0)
);

CREATE TABLE training_group_slots (
    association_id    uuid    NOT NULL,
    training_group_id uuid    NOT NULL,
    day_of_week       integer NOT NULL,
    start_time        time    NOT NULL,
    duration_minutes  integer NOT NULL,
    PRIMARY KEY (training_group_id, day_of_week, start_time),
    FOREIGN KEY (association_id, training_group_id) REFERENCES training_groups (association_id, id),
    CONSTRAINT ck_training_group_slots_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_training_group_slots_duration CHECK (duration_minutes BETWEEN 1 AND 240)
);

CREATE TABLE training_group_accepted_levels (
    association_id    uuid NOT NULL,
    training_group_id uuid NOT NULL,
    level_id          uuid NOT NULL,
    PRIMARY KEY (training_group_id, level_id),
    FOREIGN KEY (association_id, training_group_id) REFERENCES training_groups (association_id, id)
);
