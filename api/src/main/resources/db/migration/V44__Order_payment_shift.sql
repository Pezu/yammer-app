-- =============================================
-- Every order and payment belongs to a service day. Reports, the cash-up and the waiter's
-- own pages match on this id — never on a time window: a shift runs across midnight, is
-- closed by hand at an arbitrary moment, and a row must stay in the day it was rung up in
-- even if the clock says otherwise.
-- =============================================
ALTER TABLE orders ADD COLUMN shift_id UUID REFERENCES location_shift(id) ON DELETE SET NULL;
ALTER TABLE payment ADD COLUMN shift_id UUID REFERENCES location_shift(id) ON DELETE SET NULL;
CREATE INDEX idx_orders_shift ON orders(shift_id);
CREATE INDEX idx_payment_shift ON payment(shift_id);

-- Adopt what was rung up while a shift was already running (nothing else can be attributed).
UPDATE orders o SET shift_id = s.id
FROM location_shift s, order_point op
WHERE o.order_point_id = op.id
  AND op.location_id = s.location_id
  AND o.created_at >= s.opened_at
  AND (s.closed_at IS NULL OR o.created_at < s.closed_at);

UPDATE payment p SET shift_id = s.id
FROM location_shift s, order_point op
WHERE p.order_point_id = op.id
  AND op.location_id = s.location_id
  AND p.created_at >= s.opened_at
  AND (s.closed_at IS NULL OR p.created_at < s.closed_at);
