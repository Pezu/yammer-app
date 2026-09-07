-- Locations can be switched off (e.g. seasonal venues) without deleting them.
ALTER TABLE location ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
