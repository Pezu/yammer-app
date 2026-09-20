-- =============================================
-- Service day ("shift"): the location's trading period, opened and closed by a
-- manager in the backoffice. Everything a waiter sees of their own takings —
-- the Payments and Statistics pages — is scoped to the open shift instead of a
-- calendar date, the way a POS scopes to the fiscal day between two Z reports.
-- Closing prints one cash-up slip per waiter and stamps the day shut.
--
-- Orders and payments are NOT stamped with a shift id: the window [opened_at,
-- closed_at) over their created_at is enough, and the hot path stays untouched.
-- =============================================
CREATE TABLE location_shift (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    opened_by   VARCHAR(100),
    opened_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_by   VARCHAR(100),
    closed_at   TIMESTAMP
);
CREATE INDEX idx_location_shift_loc ON location_shift(location_id, opened_at DESC);
-- at most one OPEN shift per location
CREATE UNIQUE INDEX uq_location_shift_open ON location_shift(location_id) WHERE closed_at IS NULL;
