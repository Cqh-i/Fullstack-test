CREATE TABLE IF NOT EXISTS products (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id   BIGINT UNIQUE NOT NULL,
    title        TEXT NOT NULL,
    vendor       TEXT,
    product_type TEXT,
    tags         TEXT[] DEFAULT '{}'::TEXT[],
    options_json JSONB,
    created_at   TIMESTAMPTZ(3),
    updated_at   TIMESTAMPTZ(3)
);
CREATE INDEX IF NOT EXISTS ix_products_updated ON products (updated_at DESC);

CREATE TABLE IF NOT EXISTS variants (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    variant_id     BIGINT UNIQUE NOT NULL,
    product_id     BIGINT NOT NULL,
    sku            TEXT,
    image_url      TEXT,
    price          NUMERIC(10,2),
    compare_price  NUMERIC(10,2),
    available      BOOLEAN,
    position       INT,
    option1        TEXT,
    option2        TEXT,
    option3        TEXT,
    created_at     TIMESTAMPTZ(3),
    updated_at     TIMESTAMPTZ(3)
);
CREATE INDEX IF NOT EXISTS ix_variants_product_ext ON variants (product_id);
CREATE INDEX IF NOT EXISTS ix_variants_updated     ON variants (updated_at DESC);