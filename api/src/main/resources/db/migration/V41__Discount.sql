-- Per-table discount (backoffice-set percentage, NULL = none), applied per unit price when a
-- payment is taken; the payment keeps a snapshot: net amount + the percentage and money not
-- charged. PROTOCOL settlements are never discounted. Same scheme as the old project (V30).
ALTER TABLE order_point ADD COLUMN discount_percent NUMERIC(5, 2);
ALTER TABLE payment
    ADD COLUMN discount_percent NUMERIC(5, 2),
    ADD COLUMN discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;
