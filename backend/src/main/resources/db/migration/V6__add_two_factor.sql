-- Optional two-factor authentication (TOTP, RFC 6238) for each user.
-- totp_secret:         the shared secret, ENCRYPTED (AES-GCM) - not hashed, because the server must
--                      read it back to compute HMAC(secret, time). NULL = 2FA never set up.
-- totp_enabled:        false while setup is pending (secret created, first code not confirmed yet).
-- totp_last_used_step: the 30-second time step of the last accepted code. A code is only accepted
--                      for a later step, so a code seen by someone over the shoulder can't be replayed.
ALTER TABLE users
    ADD COLUMN totp_secret         VARCHAR(255) NULL,
    ADD COLUMN totp_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_last_used_step BIGINT       NULL;
