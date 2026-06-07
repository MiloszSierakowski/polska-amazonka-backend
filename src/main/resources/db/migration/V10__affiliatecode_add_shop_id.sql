ALTER TABLE affiliatecode ADD COLUMN shop_id BIGINT;

UPDATE affiliatecode ac
SET shop_id = s.id
FROM shop s
WHERE UPPER(ac.platform) = s.code;

ALTER TABLE affiliatecode ALTER COLUMN shop_id SET NOT NULL;

ALTER TABLE affiliatecode
    ADD CONSTRAINT fk_affiliatecode_shop
    FOREIGN KEY (shop_id) REFERENCES shop (id);

CREATE INDEX idx_affiliatecode_shop_type_active ON affiliatecode (shop_id, type, is_active);
