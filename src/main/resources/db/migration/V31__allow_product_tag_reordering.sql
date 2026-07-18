ALTER TABLE product_tag
    DROP CONSTRAINT IF EXISTS uq_product_tag_product_order;

CREATE INDEX IF NOT EXISTS idx_product_tag_product_order
    ON product_tag (product_id, display_order);
