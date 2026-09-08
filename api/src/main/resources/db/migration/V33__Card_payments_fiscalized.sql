-- CARD payments are settled and fiscalized immediately (as in old yammer); the softPOS
-- confirmation step is gone. Card payments left PENDING by that step are real money:
-- mark them SUCCESS, with the fiscal receipt FAILED so they can be re-issued from the
-- Payments report once a register is configured.
UPDATE payment p SET status = 'SUCCESS', fiscal_status = 'FAILED'
FROM payment_type pt
WHERE pt.id = p.payment_type_id AND pt.type = 'CARD' AND p.status = 'PENDING';
