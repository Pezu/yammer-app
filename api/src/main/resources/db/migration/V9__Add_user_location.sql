-- Optional home location for a user (must belong to the user's client).
ALTER TABLE users ADD COLUMN location_id UUID REFERENCES location(id);
CREATE INDEX idx_users_location_id ON users(location_id);
