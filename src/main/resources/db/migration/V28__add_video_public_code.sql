ALTER TABLE video
    ADD COLUMN public_code VARCHAR(20) NULL;

ALTER TABLE video
    ADD CONSTRAINT chk_video_public_code_format
        CHECK (public_code IS NULL OR public_code ~ '^[A-Z]+[0-9]+$');

ALTER TABLE video
    ADD CONSTRAINT uq_video_public_code UNIQUE (public_code);
