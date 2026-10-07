-- US-08: a booking can be cancelled by the association (deactivating the member). It is never late and
-- its credit is refundable, so it is a kind of its own. V5 is applied and never edited: the CHECK is replaced.

ALTER TABLE bookings DROP CONSTRAINT ck_bookings_cancellation_kind;
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_cancellation_kind CHECK (cancellation_kind IS NULL
    OR cancellation_kind IN ('FREE', 'LATE', 'BY_SESSION', 'BY_ASSOCIATION', 'NOT_PROMOTED'));
