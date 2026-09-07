-- =============================================
-- Online (Netopia) self-service payment for ONLINE self-pay order points.
-- A customer cart is PARKED here while the customer is on the gateway; the real
-- order + payment are created only when the gateway IPN confirms — abandoned or
-- failed payments never produce an order. The row id is the gateway reference.
-- =============================================
CREATE TABLE online_payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id      UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    session_id          UUID REFERENCES table_session(id) ON DELETE SET NULL,
    customer_session_id UUID REFERENCES customer_session(id) ON DELETE SET NULL,
    amount              NUMERIC(10,2) NOT NULL,
    items               TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ntp_id              VARCHAR(100),
    order_id            UUID,
    payment_id          UUID,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);
CREATE INDEX idx_online_payment_status ON online_payment(status);

-- Confirmed online payments land in the payments report under this catalog type.
INSERT INTO payment_type (type) VALUES ('ONLINE') ON CONFLICT (type) DO NOTHING;
