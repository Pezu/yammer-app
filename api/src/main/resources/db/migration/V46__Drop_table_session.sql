-- =============================================
-- Drop the table session. A sitting has no row of its own any more: the assignment is the
-- sitting (V45, order_point_assignment.assigned_at), money is attributed by shift_id (V44)
-- and order_point_id, and nothing has read these columns since V45 went in.
--
-- Irreversible: the closed_by/closed_at history of past sittings goes with the table. What
-- it was used for — who worked a table and what it owed — is answered today by the
-- assignments and the orders themselves.
-- =============================================
ALTER TABLE customer_session DROP COLUMN table_session_id;   -- customers hang off the point now
ALTER TABLE online_payment DROP COLUMN session_id;           -- the intent knows its order point
ALTER TABLE orders DROP COLUMN session_id;
ALTER TABLE payment DROP COLUMN session_id;

DROP TABLE table_session;
