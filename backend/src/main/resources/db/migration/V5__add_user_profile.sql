-- Profile and address details. All optional: Google sign-ups arrive without them, and GDPR data
-- minimisation says collect them when needed. Checkout (Project 2) will require them to be filled.
-- Phone and postal code are text: leading zeros, "+372" and "SW1A 1AA" don't survive as numbers.
ALTER TABLE users
    ADD COLUMN first_name  VARCHAR(50)  NULL,
    ADD COLUMN last_name   VARCHAR(50)  NULL,
    ADD COLUMN phone       VARCHAR(20)  NULL,
    ADD COLUMN street      VARCHAR(100) NULL,
    ADD COLUMN postal_code VARCHAR(20)  NULL,
    ADD COLUMN city        VARCHAR(50)  NULL,
    ADD COLUMN country     VARCHAR(56)  NULL;
