ALTER TABLE category ADD COLUMN shop_id BIGINT;

ALTER TABLE category
    ADD CONSTRAINT fk_category_shop
    FOREIGN KEY (shop_id) REFERENCES shop (id);

ALTER TABLE category
    ADD CONSTRAINT uq_category_shop_id UNIQUE (shop_id);
