-- =============================================
-- Yammer schema, consolidated.
--
-- This replaces the 46 incremental migrations the rebuild accumulated between
-- 2026-08-29 and 2026-09-20: both databases were dropped and recreated from here, so
-- the step-by-step history no longer buys anything. Read the git log for why a column
-- exists; this file is what the database IS.
--
-- Conventions: UUID primary keys with gen_random_uuid(), timestamps are naive
-- (LocalDateTime, recorded in UTC, rendered in Europe/Bucharest by the UI), money is
-- NUMERIC(10,2), and a child row that cannot outlive its parent cascades.
-- =============================================

-- ─── tenants, places, people ────────────────────────────────────────────────

CREATE TABLE client (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(255),
    logo_object VARCHAR(255)
);

-- The printed QR sheet a location's order-point codes are drawn onto.
CREATE TABLE qr_template (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL UNIQUE,
    image_object VARCHAR(255),
    qr_x         NUMERIC(6, 4) NOT NULL DEFAULT 0.3,
    qr_y         NUMERIC(6, 4) NOT NULL DEFAULT 0.3,
    qr_size      NUMERIC(6, 4) NOT NULL DEFAULT 0.4,
    label_y      NUMERIC(6, 4) NOT NULL DEFAULT 0.9,
    label_size   NUMERIC(6, 4) NOT NULL DEFAULT 0.08,
    label_color  VARCHAR(7) NOT NULL DEFAULT '#FFFFFF',
    qr_color     VARCHAR(7) NOT NULL DEFAULT '#000000',
    title        VARCHAR(100),
    title_y      NUMERIC(6, 4) NOT NULL DEFAULT 0.1
);

CREATE TABLE location (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    client_id       UUID NOT NULL REFERENCES client(id),
    active          BOOLEAN NOT NULL DEFAULT true,
    qr_template_id  UUID REFERENCES qr_template(id) ON DELETE SET NULL
);
CREATE INDEX idx_location_client_id ON location(client_id);

-- Role catalog; a user carries its roles as a text[] (no join table).
CREATE TABLE role (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(255),
    password    VARCHAR(255) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(255),
    roles       TEXT[] NOT NULL DEFAULT '{}',
    client_id   UUID REFERENCES client(id),
    -- scanned to sign in without typing a password
    qr_token    UUID UNIQUE,
    location_id UUID REFERENCES location(id)
);
CREATE INDEX idx_users_client_id ON users(client_id);
CREATE INDEX idx_users_location_id ON users(location_id);

-- ─── global catalogs ────────────────────────────────────────────────────────

CREATE TABLE vat_type (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    value NUMERIC(5, 2) NOT NULL
);

CREATE TABLE order_point_type (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE payment_type (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(100) NOT NULL UNIQUE
);

-- How a CUSTOMER settles by themselves: ONLINE (card in the QR flow) or CHECK.
CREATE TABLE self_pay_type (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(100) NOT NULL UNIQUE
);

-- ─── catalog: products, recipes, menus ──────────────────────────────────────

-- The location's product catalog. Names and descriptions are rich text (HTML);
-- price lives on the menu entry, not here.
CREATE TABLE product (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id  UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    description  TEXT,
    vat_type_id  UUID REFERENCES vat_type(id),
    image_object VARCHAR(255),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_product_location_id ON product(location_id);

-- A product made of fractions of other products (a cocktail out of its bottles).
CREATE TABLE recipe_component (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id           UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    component_product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    quantity             NUMERIC(12, 4) NOT NULL,
    sort_order           INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_recipe_component_product ON recipe_component(product_id);

CREATE TABLE menu (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_menu_location_id ON menu(location_id);

-- The menu tree. A category has orderable=false; a leaf points at a catalog
-- product and carries the price it sells for in THIS menu.
CREATE TABLE menu_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_id      UUID NOT NULL REFERENCES menu(id) ON DELETE CASCADE,
    parent_id    UUID REFERENCES menu_item(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    orderable    BOOLEAN NOT NULL DEFAULT false,
    image_object VARCHAR(255),
    sort_order   INTEGER NOT NULL DEFAULT 0,
    product_id   UUID REFERENCES product(id) ON DELETE CASCADE,
    price        NUMERIC(10, 2)
);
CREATE INDEX idx_menu_item_menu_id ON menu_item(menu_id);
CREATE INDEX idx_menu_item_parent_id ON menu_item(parent_id);
CREATE INDEX idx_menu_item_product_id ON menu_item(product_id);

-- ─── peripherals ────────────────────────────────────────────────────────────

-- Cash registers and printers. TCP ones carry an ip; MOBILE ones name the phone
-- (device_id) whose bridge owns the USB cable, device_name kept for when it is offline.
CREATE TABLE integration (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    ip          VARCHAR(255),
    type        VARCHAR(32) NOT NULL,
    connection  VARCHAR(8) NOT NULL DEFAULT 'TCP',
    device_id   VARCHAR(64),
    device_name VARCHAR(100)
);
CREATE INDEX idx_integration_location_id ON integration(location_id);
CREATE INDEX idx_integration_location_type ON integration(location_id, type);

-- ─── order points (tables, bars, service stations) ──────────────────────────

-- printer_id / cash_register_id point at integration(id) but carry no FK, as in
-- the old project: a peripheral may be swapped without touching the tables.
CREATE TABLE order_point (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id            UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name                   VARCHAR(255) NOT NULL,
    type_id                UUID NOT NULL REFERENCES order_point_type(id),
    menu_id                UUID,
    -- where this point's orders are prepared (a table routed to the kitchen)
    service_order_point_id UUID REFERENCES order_point(id) ON DELETE SET NULL,
    printer_id             UUID,
    cash_register_id       UUID,
    allow_multiple_users   BOOLEAN NOT NULL DEFAULT false,
    -- the payment types this point accepts (the pay sheet offers exactly these)
    payment_type_ids       UUID[] NOT NULL DEFAULT '{}',
    self_pay_type_id       UUID REFERENCES self_pay_type(id),
    -- customer self-ordering: ALLOW / CONFIRM (waiter approves) / DISALLOW
    self_order_mode        VARCHAR(20) NOT NULL DEFAULT 'CONFIRM',
    -- runs a tab; false = pay as you order
    keep_open              BOOLEAN NOT NULL DEFAULT true,
    nickname               VARCHAR(100),
    discount_percent       NUMERIC(5, 2)
);
CREATE INDEX idx_order_point_location_id ON order_point(location_id);
CREATE INDEX idx_order_point_type_id ON order_point(type_id);
CREATE INDEX idx_order_point_service_id ON order_point(service_order_point_id);
CREATE INDEX idx_order_point_self_pay_type_id ON order_point(self_pay_type_id);

-- Who works which table. THE ASSIGNMENT IS THE SITTING: a table takes orders while
-- somebody is assigned to it, and its current round started at the earliest
-- assigned_at. Leaving is refused while the table still owes anything, so the last
-- one out is exactly where one party ends and the next begins.
CREATE TABLE order_point_assignment (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (order_point_id, user_id)
);
CREATE INDEX idx_op_assignment_order_point_id ON order_point_assignment(order_point_id);
CREATE INDEX idx_op_assignment_user_id ON order_point_assignment(user_id);

-- ─── the service day ────────────────────────────────────────────────────────

-- A location trades in service days a manager opens and closes. Every order and
-- payment is stamped with one, and every report matches on that id rather than on a
-- time window — a day runs across midnight and is closed by hand.
CREATE TABLE location_shift (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    nickname    VARCHAR(100),
    opened_by   VARCHAR(100),
    opened_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_by   VARCHAR(100),
    closed_at   TIMESTAMP
);
CREATE INDEX idx_location_shift_loc ON location_shift(location_id, opened_at DESC);
-- at most one OPEN day per location
CREATE UNIQUE INDEX uq_location_shift_open ON location_shift(location_id) WHERE closed_at IS NULL;

-- ─── customers behind the QR codes ──────────────────────────────────────────

-- A customer device at a table: PENDING until the assigned waiter approves it. The
-- token is valid for the table's CURRENT round — one created before the round began
-- belongs to the previous party and is ignored.
CREATE TABLE customer_session (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    token          UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at     TIMESTAMP,
    decided_by     VARCHAR(100)
);
CREATE INDEX idx_customer_session_op ON customer_session(order_point_id);

-- ─── orders and money ───────────────────────────────────────────────────────

-- status: DRAFT (customer order awaiting approval — no number, invisible to the
-- bill and the board) / ORDERED / READY / DELIVERED / CANCELED.
CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no            BIGINT,
    order_point_id      UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    created_by          VARCHAR(100),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status              VARCHAR(32) NOT NULL DEFAULT 'ORDERED',
    customer_session_id UUID REFERENCES customer_session(id) ON DELETE SET NULL,
    shift_id            UUID REFERENCES location_shift(id) ON DELETE SET NULL
);
CREATE INDEX idx_orders_op_created ON orders(order_point_id, created_at);
CREATE INDEX idx_orders_shift ON orders(shift_id);

-- fiscal_status: NONE / PENDING / SUCCESS / FAILED / UNKNOWN. SUCCESS is terminal —
-- a printed receipt cannot un-print; UNKNOWN waits for an operator's verdict.
CREATE TABLE payment (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id            UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    amount                    NUMERIC(10, 2) NOT NULL,
    tip                       NUMERIC(10, 2) NOT NULL DEFAULT 0,
    payment_type_id           UUID REFERENCES payment_type(id),
    created_by                VARCHAR(100),
    created_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shift_id                  UUID REFERENCES location_shift(id) ON DELETE SET NULL,
    status                    VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    fiscal_status             VARCHAR(16) NOT NULL DEFAULT 'NONE',
    receipt_number            VARCHAR(64),
    fiscal_sent_at            TIMESTAMP,
    -- the bridge device this payment is pinned to (first writer wins)
    fiscal_device             VARCHAR(100),
    fiscal_reprint_authorized BOOLEAN NOT NULL DEFAULT false,
    -- snapshot of the table's discount at the time, and the money not charged
    discount_percent          NUMERIC(5, 2),
    discount_amount           NUMERIC(10, 2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_payment_op_created ON payment(order_point_id, created_at);
CREATE INDEX idx_payment_shift ON payment(shift_id);
CREATE INDEX idx_payment_fiscal_pending ON payment(created_at) WHERE fiscal_status = 'PENDING';

-- One line of an order. payment_id NULL = still unpaid; original_price is set only on
-- the remainder of a partially-paid unit.
CREATE TABLE order_item (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id       UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id   UUID,
    name           TEXT NOT NULL,
    price          NUMERIC(10, 2),
    quantity       INTEGER NOT NULL,
    payment_id     UUID REFERENCES payment(id) ON DELETE SET NULL,
    original_price NUMERIC(10, 2)
);
CREATE INDEX idx_order_item_order ON order_item(order_id);
CREATE INDEX idx_order_item_payment ON order_item(payment_id);

-- A parked customer cart while the card payment is with Netopia: the order is only
-- created once the gateway confirms.
CREATE TABLE online_payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id      UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    customer_session_id UUID REFERENCES customer_session(id) ON DELETE SET NULL,
    amount              NUMERIC(10, 2) NOT NULL,
    items               TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ntp_id              VARCHAR(100),
    order_id            UUID,
    payment_id          UUID,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);
CREATE INDEX idx_online_payment_status ON online_payment(status);
