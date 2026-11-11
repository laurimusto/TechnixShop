-- NULL password = account has no password (Google-only); password login must reject it.
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Google's permanent user id (the ID token's "sub" claim). Emails can change, sub never does.
-- UNIQUE allows many NULLs in Postgres, so unlinked accounts don't collide.
ALTER TABLE users ADD COLUMN google_sub VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uk_users_google_sub UNIQUE (google_sub);
