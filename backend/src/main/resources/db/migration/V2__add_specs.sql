-- Typed EAV for category-specific filterable specs (RAM, GPU, CPU, display size, ...).
-- New specs can be added later with INSERT statements only - no further migrations needed
-- for the spec definitions themselves.

CREATE TABLE specs (
    spec_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key VARCHAR(100) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('NUMBER', 'TEXT', 'ENUM')),
    unit VARCHAR(20),
    filterable BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE category_specs (
    category_id BIGINT NOT NULL REFERENCES categories (category_id) ON DELETE CASCADE,
    spec_id BIGINT NOT NULL REFERENCES specs (spec_id) ON DELETE CASCADE,
    PRIMARY KEY (category_id, spec_id)
);

CREATE TABLE product_spec_values (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products (product_id) ON DELETE CASCADE,
    spec_id BIGINT NOT NULL REFERENCES specs (spec_id) ON DELETE CASCADE,
    value_number DECIMAL(12, 2),
    value_text VARCHAR(255),
    UNIQUE (product_id, spec_id)
);

CREATE INDEX idx_product_spec_values_spec_number ON product_spec_values (spec_id, value_number);
CREATE INDEX idx_product_spec_values_spec_text ON product_spec_values (spec_id, value_text);

-- Seed the three known specs and attach them to both categories.
-- Categories themselves (Phones, Laptops) are seed data, inserted separately.
INSERT INTO specs (key, label, data_type, unit, filterable) VALUES
    ('ram_gb', 'RAM (GB)', 'NUMBER', 'GB', TRUE),
    ('cpu', 'CPU', 'TEXT', NULL, TRUE),
    ('gpu', 'GPU', 'TEXT', NULL, TRUE),
    ('display_size_in', 'Display size (in)', 'NUMBER', 'in', TRUE);
