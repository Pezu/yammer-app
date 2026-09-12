-- The waiter UI no longer switches self-ordering per table: every point runs CONFIRM
-- (customers join and order through the waiter's Approvals page).
UPDATE order_point SET self_order_mode = 'CONFIRM' WHERE self_order_mode <> 'CONFIRM';
