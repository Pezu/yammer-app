-- Copy T1.1's printer, cash register and service point to every other TABLE point
-- (names T{n}.{m}) at the same location. Run once; safe to re-run.

BEGIN;

UPDATE yammer.order_point op
SET printer_id             = src.printer_id,
    cash_register_id       = src.cash_register_id,
    service_order_point_id = src.service_order_point_id
FROM yammer.order_point src
JOIN yammer.order_point_type t ON t.id = src.type_id
WHERE src.name = 'T1.1'
  AND src.location_id = '4f884711-7c5e-41a7-9aec-338208ef3dba'
  AND t.type = 'TABLE'
  AND op.location_id = src.location_id
  AND op.type_id = src.type_id
  AND op.name ~ '^T[0-9]+\.[0-9]+$'
  AND op.id <> src.id;

COMMIT;

-- Check:
-- SELECT name, printer_id, cash_register_id, service_order_point_id
-- FROM yammer.order_point WHERE location_id = '4f884711-7c5e-41a7-9aec-338208ef3dba' ORDER BY name;
