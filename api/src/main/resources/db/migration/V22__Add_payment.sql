-- =============================================
-- Payment  (taken against an order point's running bill)
--   amount          — the unpaid lines settled by this payment (tip on top)
--   payment_type_id — from the payment_type catalog, restricted per order point
-- =============================================
CREATE TABLE payment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id  UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    amount          NUMERIC(10, 2) NOT NULL,
    tip             NUMERIC(10, 2) NOT NULL DEFAULT 0,
    payment_type_id UUID REFERENCES payment_type(id),
    created_by      VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payment_op_created ON payment(order_point_id, created_at);

-- The payment that settled an order line; NULL while unpaid.
ALTER TABLE order_item ADD COLUMN payment_id UUID REFERENCES payment(id) ON DELETE SET NULL;
CREATE INDEX idx_order_item_payment ON order_item(payment_id);
