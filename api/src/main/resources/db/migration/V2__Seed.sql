-- =============================================
-- Yammer seed: the global catalogs every tenant shares, plus the operator account
-- that creates the first client. Nothing tenant-specific belongs here — a client's
-- products and menus are loaded from db-seeds/ or entered in the backoffice.
--
-- Every insert is idempotent so a re-run (or a hand-edited catalog) is harmless.
-- =============================================

-- Roles a user can carry (users.roles is a text[]; there is no join table).
INSERT INTO role (role) VALUES
    ('WAITER'),
    ('ADMIN'),
    ('SERVICE'),
    ('SUPER'),
    ('WATCHER')
ON CONFLICT (role) DO NOTHING;

-- The default SUPER operator: no client scope, so it can manage everything.
-- The password is seeded as MD5; AuthService re-hashes it to BCrypt on first login.
INSERT INTO users (username, name, password, phone, email, roles)
VALUES ('office@yammer.ro', 'Super Admin', md5('Cinci_55!!'), '0723335651', 'rpaise@gmail.com', ARRAY['SUPER'])
ON CONFLICT (username) DO NOTHING;

-- What an order point can be: a kitchen/bar station that prepares orders, a bar that
-- sells over the counter, or a table.
INSERT INTO order_point_type (type) VALUES
    ('SERVICE'),
    ('BAR'),
    ('TABLE')
ON CONFLICT (type) DO NOTHING;

-- How a bill is settled. CASH and CARD print a fiscal receipt; PROTOCOL and PO close
-- consumption without collecting money; ONLINE is the customer's own card payment.
INSERT INTO payment_type (type) VALUES
    ('CARD'),
    ('CASH'),
    ('PROTOCOL'),
    ('PO'),
    ('ONLINE')
ON CONFLICT (type) DO NOTHING;

-- How a customer settles by themselves at a QR table.
INSERT INTO self_pay_type (type) VALUES
    ('ONLINE'),
    ('CHECK')
ON CONFLICT (type) DO NOTHING;

-- Romanian VAT rates (percent).
INSERT INTO vat_type (value)
SELECT v FROM (VALUES (21.00), (11.00), (0.00)) AS seed(v)
WHERE NOT EXISTS (SELECT 1 FROM vat_type);

-- The QR sheet the printed table codes are drawn onto.
INSERT INTO qr_template (name, image_object, qr_x, qr_y, qr_size, label_y, label_size, label_color)
VALUES ('Rendezvous', 'classpath:qr-templates/rendezvous.png', 0.2634, 0.2179, 0.4732, 0.7714, 0.0786, '#080814')
ON CONFLICT (name) DO NOTHING;
