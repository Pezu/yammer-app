-- =============================================
-- Payment type  (global catalog, like order_point_type)
-- =============================================
CREATE TABLE payment_type (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO payment_type (type) VALUES
    ('CARD'),
    ('CASH'),
    ('PROTOCOL'),
    ('PO')
ON CONFLICT (type) DO NOTHING;

-- Order points: whether several users may work the point at once, and how it is paid.
ALTER TABLE order_point ADD COLUMN allow_multiple_users BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE order_point ADD COLUMN payment_type_id UUID REFERENCES payment_type(id);
CREATE INDEX idx_order_point_payment_type_id ON order_point(payment_type_id);
