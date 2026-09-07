-- =============================================
-- Payment lifecycle per payment type:
--   CASH      -> SUCCESS immediately + fiscal-print request to the bridge (logged for now)
--   CARD      -> PENDING until the softPOS callback marks SUCCESS / FAILED
--   PROTOCOL  -> SUCCESS immediately, no fiscal printer (as in old yammer)
--   PO        -> SUCCESS immediately, no fiscal printer
-- FAILED card payments release their order lines back to unpaid.
-- =============================================
ALTER TABLE payment ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS';
