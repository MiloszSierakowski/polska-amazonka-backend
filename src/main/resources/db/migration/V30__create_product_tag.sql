CREATE TABLE product_tag (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    value VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT fk_product_tag_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT chk_product_tag_value_not_blank CHECK (btrim(value) <> ''),
    CONSTRAINT chk_product_tag_display_order_non_negative CHECK (display_order >= 0),
    CONSTRAINT uq_product_tag_product_order UNIQUE (product_id, display_order)
);

CREATE UNIQUE INDEX uq_product_tag_product_value_ci
    ON product_tag (product_id, lower(value));
