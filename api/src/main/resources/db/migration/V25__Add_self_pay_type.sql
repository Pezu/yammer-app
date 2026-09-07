-- =============================================
-- Self pay type (global catalog): how a CUSTOMER self-pays at an order point
-- (ONLINE = pay in the customer flow, CHECK = ask for the check). Replaces the
-- never-used order_point.pay_later boolean.
-- =============================================
CREATE TABLE self_pay_type (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO self_pay_type (type) VALUES
    ('ONLINE'),
    ('CHECK')
ON CONFLICT (type) DO NOTHING;

ALTER TABLE order_point ADD COLUMN self_pay_type_id UUID REFERENCES self_pay_type(id);
CREATE INDEX idx_order_point_self_pay_type_id ON order_point(self_pay_type_id);

ALTER TABLE order_point DROP COLUMN pay_later;
