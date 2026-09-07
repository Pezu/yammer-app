-- Products keep insertion order (newest first in listings).
ALTER TABLE product ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
