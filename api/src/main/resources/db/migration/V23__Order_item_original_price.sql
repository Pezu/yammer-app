-- When a fixed-sum payment splits a single unit, the unpaid remainder line keeps
-- the unit's ORIGINAL price here so the bill can show "4.00 (of 7.00)".
ALTER TABLE order_item ADD COLUMN original_price NUMERIC(10, 2);
