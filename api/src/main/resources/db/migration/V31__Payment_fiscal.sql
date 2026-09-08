-- Fiscal receipt tracking per payment (the payment row IS the fiscal outbox):
--   NONE     — nothing to fiscalize (PROTOCOL / PO, a CARD payment still pending at the terminal)
--   PENDING  — RECEIPT pushed (or about to be) to the on-prem bridge, awaiting RECEIPT_RESULT
--   SUCCESS  — printed (terminal; a printed fiscal receipt cannot un-print)
--   FAILED   — not printed; re-issued manually from the payments report
--   UNKNOWN  — ambiguous print; an operator verifies on the register and resolves it
-- Existing payments were never fiscalized (the bridge did not exist yet) → NONE.
ALTER TABLE payment ADD COLUMN fiscal_status VARCHAR(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE payment ADD COLUMN receipt_number VARCHAR(64);
-- when the RECEIPT was last pushed (the sweeper's deadline counts from here)
ALTER TABLE payment ADD COLUMN fiscal_sent_at TIMESTAMP;
-- the bridge device that first handled the receipt: every re-dispatch goes there only
ALTER TABLE payment ADD COLUMN fiscal_device VARCHAR(100);
-- operator verified an UNKNOWN receipt as NOT printed → the next RECEIPT carries clearIntent
ALTER TABLE payment ADD COLUMN fiscal_reprint_authorized BOOLEAN NOT NULL DEFAULT FALSE;
-- the sweeper scans PENDING rows only; a partial index keeps that scan tiny
CREATE INDEX idx_payment_fiscal_pending ON payment (created_at) WHERE fiscal_status = 'PENDING';
