-- Lets the backend invalidate previously-issued access tokens on demand (password
-- change / reset) without a server-side token blacklist: each JWT embeds the
-- token_version that was current at issuance, and the filter rejects a token whose
-- embedded version no longer matches the user's current value.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

-- Distinguishes locally-registered accounts from ones created via an OAuth
-- provider (Google, for now). OAuth-created accounts get an unusable random
-- password hash at creation time; they can still set a real password later via
-- the password-reset flow.
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD CONSTRAINT ck_users_auth_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE'));
