-- =============================================
-- Customer self-ordering with waiter approval.
--  * customer_session: a customer device joining an OPEN table session (token = the
--    value the customer's browser stores; scanning again resumes the same session).
--    PENDING until the assigned waiter approves; dies with the table session.
--  * order_point.self_order_mode: ALLOW (customer orders go straight in) /
--    CONFIRM (customer orders enter APPROVAL status until the waiter approves) /
--    DISALLOW (customer page is just a menu).
--  * orders.customer_session_id: which customer device placed the order.
-- =============================================
CREATE TABLE customer_session (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_session_id UUID NOT NULL REFERENCES table_session(id) ON DELETE CASCADE,
    token            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at       TIMESTAMP,
    decided_by       VARCHAR(100)
);
CREATE INDEX idx_customer_session_ts ON customer_session(table_session_id);

ALTER TABLE order_point ADD COLUMN self_order_mode VARCHAR(20) NOT NULL DEFAULT 'CONFIRM';
ALTER TABLE orders ADD COLUMN customer_session_id UUID REFERENCES customer_session(id) ON DELETE SET NULL;
