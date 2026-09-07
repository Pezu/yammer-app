-- =============================================
-- Orders  (placed by a waiter at an order point)
--   order_no — human-friendly sequence per client (max+1 at placement)
--   status   — ORDERED for now; the delivery/kanban flow arrives with later ports
-- =============================================
CREATE TABLE orders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no       BIGINT NOT NULL,
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    created_by     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         VARCHAR(32) NOT NULL DEFAULT 'ORDERED'
);
CREATE INDEX idx_orders_op_created ON orders(order_point_id, created_at);

-- Order lines snapshot name+price at placement so later menu edits don't rewrite bills.
CREATE TABLE order_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id UUID,
    name         TEXT NOT NULL,
    price        NUMERIC(10, 2),
    quantity     INTEGER NOT NULL
);
CREATE INDEX idx_order_item_order ON order_item(order_id);
