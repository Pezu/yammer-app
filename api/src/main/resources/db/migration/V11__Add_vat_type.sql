-- =============================================
-- VAT type  (global catalog data, identified by its percentage value)
-- =============================================
CREATE TABLE vat_type (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    value NUMERIC(5, 2) NOT NULL
);

-- VAT catalog (percentage values).
INSERT INTO vat_type (value) VALUES
    (21.00),
    (11.00),
    (0.00);
