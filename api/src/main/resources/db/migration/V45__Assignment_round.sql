-- =============================================
-- The assignment IS the sitting. A table exists to let the waiters assigned to it take
-- orders; they leave it (un-assign) once there is nothing left to settle, and that moment
-- is where one party ends and the next begins. So the round a table is in is dated by its
-- earliest active assignment, and table_session stops being written.
--
-- Customer QR sessions hang off the order point instead of the table session; a token is
-- valid while it belongs to the table's current round (created at or after its start).
--
-- table_session, orders.session_id and payment.session_id are left in place, unwritten, so
-- existing history stays readable; a later migration drops them.
-- =============================================
ALTER TABLE order_point_assignment
    ADD COLUMN assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE customer_session
    ADD COLUMN order_point_id UUID REFERENCES order_point(id) ON DELETE CASCADE;
CREATE INDEX idx_customer_session_op ON customer_session(order_point_id);

-- A customer session now belongs to a point, not to a session row.
UPDATE customer_session cs SET order_point_id = ts.order_point_id
FROM table_session ts
WHERE cs.table_session_id = ts.id;

ALTER TABLE customer_session ALTER COLUMN table_session_id DROP NOT NULL;

-- Tables already being worked keep their current sitting: the round started when the
-- session did, so today's bills and customer tokens survive the switch.
UPDATE order_point_assignment a SET assigned_at = ts.opened_at
FROM table_session ts
WHERE ts.order_point_id = a.order_point_id
  AND ts.closed_at IS NULL
  AND ts.opened_at IS NOT NULL;
