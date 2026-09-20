-- The manager's own label for a service day ("Revelion", "terasa inchisa"); optional,
-- shown and edited on the backoffice Shifts page.
ALTER TABLE location_shift ADD COLUMN nickname VARCHAR(100);
