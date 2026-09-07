-- =============================================
-- Table session: opened when a table gets its first assignee, closed when the
-- last assignee leaves (only possible once the session is fully settled).
-- Orders and payments belong to a session; the waiter's bill (paid + unpaid)
-- shows only the CURRENT open session.
-- =============================================
CREATE TABLE table_session (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    opened_by      VARCHAR(100),
    opened_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_by      VARCHAR(100),
    closed_at      TIMESTAMP
);
CREATE INDEX idx_table_session_op ON table_session(order_point_id);
-- at most one OPEN session per order point
CREATE UNIQUE INDEX uq_table_session_open ON table_session(order_point_id) WHERE closed_at IS NULL;

ALTER TABLE orders ADD COLUMN session_id UUID REFERENCES table_session(id) ON DELETE SET NULL;
ALTER TABLE payment ADD COLUMN session_id UUID REFERENCES table_session(id) ON DELETE SET NULL;
CREATE INDEX idx_orders_session ON orders(session_id);

-- Backfill: every currently-assigned table gets an open session, adopting its
-- existing orders and payments so in-flight tables keep their bills.
INSERT INTO table_session (order_point_id)
SELECT DISTINCT order_point_id FROM order_point_assignment;

UPDATE orders o SET session_id = ts.id
FROM table_session ts
WHERE ts.order_point_id = o.order_point_id AND ts.closed_at IS NULL;

UPDATE payment p SET session_id = ts.id
FROM table_session ts
WHERE ts.order_point_id = p.order_point_id AND ts.closed_at IS NULL;
