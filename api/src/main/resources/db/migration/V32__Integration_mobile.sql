-- Peripherals get a MOBILE type: a bridge phone (its device_id = the id the app announces
-- in HELLO). Cash registers / printers attach to a MOBILE row via bridge_id instead of
-- carrying a raw device id; connection 'MOBILE' replaces the old 'USB'.
ALTER TABLE integration ADD COLUMN bridge_id UUID REFERENCES integration(id) ON DELETE SET NULL;
CREATE INDEX idx_integration_bridge ON integration(bridge_id);
UPDATE integration SET connection = 'MOBILE' WHERE connection = 'USB';
