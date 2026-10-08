-- Venue aggregate (US-02), Payment aggregate (US-21, RN-17, RN-19), the price a subscription was sold at (US-20) and
-- uniqueness of pending join requests (US-05).
-- training_groups.venue_id stays without a foreign key (V4 is applied and never edited): the delete use case
-- refuses to remove a venue that an active group uses, serialised with group creation by the venue's row lock.

CREATE TABLE venues (
    id             uuid         PRIMARY KEY,
    association_id uuid         NOT NULL REFERENCES associations (id),
    name           varchar(100) NOT NULL,
    address        varchar(200) NOT NULL,
    courts         integer      NOT NULL,
    version        bigint       NOT NULL,
    CONSTRAINT uq_venues_association_id_id UNIQUE (association_id, id),
    CONSTRAINT ck_venues_courts CHECK (courts >= 1),
    CONSTRAINT ck_venues_version CHECK (version >= 0)
);

-- Payments are append-only (RN-19): a mistake is corrected by a reversal, a second row with reversal_of set
-- that counts negatively. There is no version column because a row never changes, and the trigger below makes
-- that true for anyone with database access too. Only ids and the audit (recorded_by, recorded_at): no personal data.
CREATE TABLE payments (
    id              uuid        PRIMARY KEY,
    association_id  uuid        NOT NULL REFERENCES associations (id),
    subscription_id uuid        NOT NULL,
    amount_cents    bigint      NOT NULL,
    paid_on         date        NOT NULL,
    method          varchar(20) NOT NULL,
    recorded_by     uuid        NOT NULL,
    recorded_at     timestamptz NOT NULL,
    reversal_of     uuid,
    CONSTRAINT uq_payments_association_id_id UNIQUE (association_id, id),
    CONSTRAINT uq_payments_association_subscription_id UNIQUE (association_id, subscription_id, id),
    -- a payment is reversed at most once
    CONSTRAINT uq_payments_reversal_of UNIQUE (reversal_of),
    FOREIGN KEY (association_id, subscription_id) REFERENCES subscriptions (association_id, id),
    -- a reversal reverses a payment of the same subscription (and so of the same association)
    CONSTRAINT fk_payments_reversal_of FOREIGN KEY (association_id, subscription_id, reversal_of)
        REFERENCES payments (association_id, subscription_id, id),
    CONSTRAINT ck_payments_amount CHECK (amount_cents > 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'TRANSFER', 'MB_WAY')),
    CONSTRAINT ck_payments_not_self_reversal CHECK (reversal_of IS NULL OR reversal_of <> id)
);

CREATE INDEX ix_payments_subscription ON payments (association_id, subscription_id, recorded_at);

CREATE FUNCTION payments_are_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'payments are append-only (RN-19): record a reversal instead of changing or deleting a payment';
END;
$$;

CREATE TRIGGER trg_payments_append_only
    BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION payments_are_append_only();

-- TRUNCATE skips row triggers, so it gets a statement-level one.
CREATE TRIGGER trg_payments_no_truncate
    BEFORE TRUNCATE ON payments
    FOR EACH STATEMENT EXECUTE FUNCTION payments_are_append_only();

-- A reversal cannot be reversed (a CHECK cannot look at another row). Rows never change, so checking on INSERT is enough.
CREATE FUNCTION payments_reject_reversal_of_reversal() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.reversal_of IS NOT NULL AND EXISTS (SELECT 1 FROM payments WHERE id = NEW.reversal_of AND reversal_of IS NOT NULL) THEN
        RAISE EXCEPTION 'a reversal cannot be reversed: record a new payment instead';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payments_no_reversal_of_reversal
    BEFORE INSERT ON payments
    FOR EACH ROW EXECUTE FUNCTION payments_reject_reversal_of_reversal();

-- One pending request per email and association: the use case checks first, this is the backstop for two
-- concurrent submissions. A decided or erased request (placeholder email) no longer counts.
CREATE UNIQUE INDEX uq_join_requests_pending_email ON join_requests (association_id, email) WHERE status = 'PENDING';

-- A subscription keeps the price its plan had when it was assigned, like it keeps the plan's terms (V3): editing a
-- plan must not change what existing subscriptions owe. Existing rows get their plan's current price.
ALTER TABLE subscriptions ADD COLUMN price_cents bigint;
UPDATE subscriptions s
SET price_cents = (SELECT p.price_cents FROM plans p WHERE p.association_id = s.association_id AND p.id = s.plan_id);
UPDATE subscriptions SET price_cents = 0 WHERE price_cents IS NULL;
ALTER TABLE subscriptions ALTER COLUMN price_cents SET NOT NULL;
ALTER TABLE subscriptions ADD CONSTRAINT ck_subscriptions_price CHECK (price_cents >= 0);
