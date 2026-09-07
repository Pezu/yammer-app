-- An order point can accept several payment types — replace the single FK with a set.
ALTER TABLE order_point DROP COLUMN payment_type_id;
ALTER TABLE order_point ADD COLUMN payment_type_ids UUID[] NOT NULL DEFAULT '{}';
