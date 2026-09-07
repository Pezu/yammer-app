-- =============================================
-- Integration  (peripherals: printers + cash registers, per location)
--   connection: how the bridge reaches the device — over the LAN (TCP, via the
--   stored IP) or attached directly to the bridge device over USB.
--   device_id: binds a USB integration to the bridge device the register is
--   attached to (announced by each bridge in its HELLO frame).
-- =============================================
CREATE TABLE integration (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    ip          VARCHAR(255),
    type        VARCHAR(32) NOT NULL,
    connection  VARCHAR(8) NOT NULL DEFAULT 'TCP',
    device_id   VARCHAR(64)
);
CREATE INDEX idx_integration_location_id ON integration(location_id);
CREATE INDEX idx_integration_location_type ON integration(location_id, type);
