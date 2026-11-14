-- Moment the password last changed. Access tokens with iat before this are rejected,
-- so a password reset kills every existing session. NULL = never changed.
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ NULL;
