-- Government-backed identity verification (DigiLocker). We store only what the provider returns:
-- the verified name and a pseudonymous reference id. No Aadhaar number, no document images.
ALTER TABLE users ADD COLUMN identity_provider VARCHAR(20);
ALTER TABLE users ADD COLUMN identity_name VARCHAR(120);
ALTER TABLE users ADD COLUMN identity_ref VARCHAR(64);
ALTER TABLE users ADD COLUMN identity_verified_at TIMESTAMP;
CREATE INDEX idx_users_identity_ref ON users (identity_ref);
