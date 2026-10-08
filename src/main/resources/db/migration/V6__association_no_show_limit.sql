-- RN-11 (decided 7/10/2026): reaching this many no-shows in a calendar month warns the member and the
-- administrators; it never blocks booking. Configurable per association, 3 by default.

ALTER TABLE associations
    ADD COLUMN no_show_limit integer NOT NULL DEFAULT 3,
    ADD CONSTRAINT ck_associations_no_show_limit CHECK (no_show_limit >= 1);
