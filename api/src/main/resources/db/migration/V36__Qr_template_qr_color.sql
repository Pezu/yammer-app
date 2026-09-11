-- The QR modules' colour per template (background stays transparent, so a dark frame
-- gets a white QR). The Rendezvous frame is one card per QR: dark centre square with
-- a white QR filling the inner yellow square and the table name below it in the border's yellow.
ALTER TABLE qr_template ADD COLUMN qr_color VARCHAR(7) NOT NULL DEFAULT '#000000';

UPDATE qr_template
SET image_object = 'classpath:qr-templates/rendezvous.png',
    qr_x = 0.1911, qr_y = 0.1911, qr_size = 0.6214, qr_color = '#FFFFFF',
    label_y = 0.9073, label_size = 0.0625, label_color = '#FFD200'
WHERE name = 'Rendezvous';
