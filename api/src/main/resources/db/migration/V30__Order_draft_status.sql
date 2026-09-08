-- Customer orders awaiting the waiter's confirmation are DRAFTs: no order number yet
-- (assigned on approval), invisible everywhere but the Approvals page, deleted when denied
-- or when their table closes. Existing APPROVAL orders on closed sessions were never
-- accepted, so they are removed; the rest become drafts.
ALTER TABLE orders ALTER COLUMN order_no DROP NOT NULL;

DELETE FROM order_item WHERE order_id IN (
    SELECT o.id FROM orders o JOIN table_session ts ON ts.id = o.session_id
    WHERE o.status = 'APPROVAL' AND ts.closed_at IS NOT NULL);
DELETE FROM orders WHERE status = 'APPROVAL' AND session_id IN (
    SELECT id FROM table_session WHERE closed_at IS NOT NULL);

UPDATE orders SET status = 'DRAFT', order_no = NULL WHERE status = 'APPROVAL';
