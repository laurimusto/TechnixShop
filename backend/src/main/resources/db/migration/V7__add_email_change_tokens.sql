-- Pending email changes. The email only changes after the link sent to the NEW address is clicked,
-- which proves the user owns that inbox. Until then the new address waits here, not in users.email.
-- Same shape as password_reset_tokens: only the SHA-256 hash of the link token is stored.
-- No "used" column: a confirmed or replaced request is simply deleted.
CREATE TABLE email_change_tokens (
    token_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    new_email   VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMP    NOT NULL
);

-- requesting a new change deletes the user's older requests: lookup by user_id
CREATE INDEX idx_email_change_tokens_user_id ON email_change_tokens (user_id);
