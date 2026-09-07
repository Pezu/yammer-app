-- QR-login secret becomes a resettable UUID. Re-created (dropping any tokens issued
-- while it was a varchar) — resetting the value is exactly what invalidates old QRs.
ALTER TABLE users DROP COLUMN qr_token;
ALTER TABLE users ADD COLUMN qr_token UUID UNIQUE;
