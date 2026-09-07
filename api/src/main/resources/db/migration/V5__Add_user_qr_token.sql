-- Per-user QR-login secret. The user's QR code encodes a URL embedding this token;
-- opening it signs the user in directly. NULL = no QR requested yet (generated on
-- first request from the Users page).
ALTER TABLE users ADD COLUMN qr_token VARCHAR(64) UNIQUE;
