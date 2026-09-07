-- =============================================
-- Order point type  (global catalog, like role)
-- =============================================
CREATE TABLE order_point_type (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO order_point_type (type) VALUES
    ('SERVICE'),
    ('BAR'),
    ('TABLE')
ON CONFLICT (type) DO NOTHING;
