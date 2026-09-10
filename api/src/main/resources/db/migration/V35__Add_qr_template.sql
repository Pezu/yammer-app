-- =============================================
-- QR template  (global catalog, like order_point_type)
--   A frame the order-point QR sheets are printed on: a background image plus where the
--   QR code and the order point's name go on it. Positions/sizes are fractions of the
--   image (x/size of its width, y of its height) so any resolution works.
--   image_object is a storage object key, or "classpath:<path>" for art bundled with the API
--   (the Rendezvous frame: 560x560 PNG; the QR and the table name sit inside its white centre square).
-- =============================================
CREATE TABLE qr_template (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL UNIQUE,
    image_object VARCHAR(255),
    qr_x         NUMERIC(6,4) NOT NULL DEFAULT 0.3,
    qr_y         NUMERIC(6,4) NOT NULL DEFAULT 0.3,
    qr_size      NUMERIC(6,4) NOT NULL DEFAULT 0.4,
    label_y      NUMERIC(6,4) NOT NULL DEFAULT 0.9,
    label_size   NUMERIC(6,4) NOT NULL DEFAULT 0.08,
    label_color  VARCHAR(7)   NOT NULL DEFAULT '#FFFFFF'
);

-- A location prints its QR sheets on this frame (none = the plain 3-column grid).
ALTER TABLE location ADD COLUMN qr_template_id UUID REFERENCES qr_template(id) ON DELETE SET NULL;

INSERT INTO qr_template (name, image_object, qr_x, qr_y, qr_size, label_y, label_size, label_color)
VALUES ('Rendezvous', 'classpath:qr-templates/rendezvous.png', 0.2634, 0.2179, 0.4732, 0.7714, 0.0786, '#080814')
ON CONFLICT (name) DO NOTHING;
