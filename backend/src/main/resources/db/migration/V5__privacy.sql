-- Bumped on password change/reset so every previously issued login token stops working.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- Set when an account is deleted (the row is anonymised, not removed, so other people's booking history stays intact).
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
