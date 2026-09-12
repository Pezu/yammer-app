-- keep_open: the point runs a tab — orders pile up on the open session and are paid later
-- (tables). Off = pay-as-you-order (bars): placing an order goes straight to payment.
ALTER TABLE order_point ADD COLUMN keep_open BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE order_point op SET keep_open = FALSE
FROM order_point_type t
WHERE t.id = op.type_id AND t.type = 'BAR';
