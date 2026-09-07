-- =============================================
-- Order point  (belongs to a location; typed via the order_point_type catalog)
--   menu_id / printer_id / cash_register_id are plain UUIDs for now — their
--   target tables (menu, integrations) are not ported yet; FKs come with them.
-- =============================================
CREATE TABLE order_point (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id            UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name                   VARCHAR(255) NOT NULL,
    type_id                UUID NOT NULL REFERENCES order_point_type(id),
    pay_later              BOOLEAN NOT NULL DEFAULT false,
    menu_id                UUID,
    service_order_point_id UUID REFERENCES order_point(id) ON DELETE SET NULL,
    printer_id             UUID,
    cash_register_id       UUID
);
CREATE INDEX idx_order_point_location_id ON order_point(location_id);
CREATE INDEX idx_order_point_type_id ON order_point(type_id);
CREATE INDEX idx_order_point_service_id ON order_point(service_order_point_id);
