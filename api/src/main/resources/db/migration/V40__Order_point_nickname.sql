-- A free label per order point (e.g. the guest's name on a reserved table), shown to the
-- waiter under the table name. Not configuration: a split slot does not inherit it.
ALTER TABLE order_point ADD COLUMN nickname VARCHAR(100);
