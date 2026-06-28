ALTER TABLE video
    ADD COLUMN promotion_start_at TIMESTAMPTZ NULL,
    ADD COLUMN promotion_end_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT chk_video_promotion_dates
        CHECK (
            (promotion_start_at IS NULL AND promotion_end_at IS NULL)
            OR (promotion_start_at IS NOT NULL AND promotion_end_at IS NOT NULL AND promotion_start_at < promotion_end_at)
        );

CREATE INDEX idx_video_promotion_dates ON video (promotion_start_at, promotion_end_at);

ALTER TABLE videoproduct
    ADD COLUMN promo_code VARCHAR(255) NULL;
