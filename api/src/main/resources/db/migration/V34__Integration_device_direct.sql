-- Routing simplified: a register / printer with connection MOBILE points straight at a
-- bridge phone (device_id from the phone's HELLO, device_name remembered for display while
-- it is offline). The intermediate MOBILE rows of V32 are folded back into their devices.
ALTER TABLE integration ADD COLUMN device_name VARCHAR(100);
UPDATE integration i SET device_id = m.device_id, device_name = m.name
FROM integration m WHERE i.bridge_id = m.id;
DELETE FROM integration WHERE type = 'MOBILE';
DROP INDEX IF EXISTS idx_integration_bridge;
ALTER TABLE integration DROP COLUMN bridge_id;
