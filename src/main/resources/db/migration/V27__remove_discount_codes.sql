DELETE FROM affiliatecode
WHERE type = 'DISCOUNT';

ALTER TABLE affiliatecode
    DROP CONSTRAINT affiliatecode_type_chk;

ALTER TABLE affiliatecode
    ADD CONSTRAINT affiliatecode_type_chk CHECK (type = 'AFFILIATE');
