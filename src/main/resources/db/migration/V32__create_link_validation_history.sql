CREATE TABLE link_validation_run (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    selected_count INTEGER NOT NULL DEFAULT 0,
    checked_count INTEGER NOT NULL DEFAULT 0,
    working_count INTEGER NOT NULL DEFAULT 0,
    broken_count INTEGER NOT NULL DEFAULT 0,
    uncertain_count INTEGER NOT NULL DEFAULT 0,
    blocked_count INTEGER NOT NULL DEFAULT 0,
    technical_error_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    triggered_by VARCHAR(255),
    CONSTRAINT ck_link_validation_run_source CHECK (source IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT ck_link_validation_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'))
);

CREATE TABLE link_validation_run_item (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    link_id BIGINT,
    product_id BIGINT,
    product_name_snapshot VARCHAR(500),
    original_url TEXT,
    normalized_url TEXT,
    final_url TEXT,
    verification_status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    http_status INTEGER,
    duration_ms BIGINT NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL,
    technical_error BOOLEAN NOT NULL DEFAULT FALSE,
    previous_is_broken BOOLEAN,
    new_is_broken BOOLEAN,
    previous_needs_review BOOLEAN,
    new_needs_review BOOLEAN,
    CONSTRAINT fk_link_validation_item_run FOREIGN KEY (run_id)
        REFERENCES link_validation_run(id) ON DELETE CASCADE,
    CONSTRAINT ck_link_validation_item_status CHECK (
        verification_status IN ('WORKING', 'BROKEN', 'UNCERTAIN', 'BLOCKED', 'TECHNICAL_ERROR')
    )
);

CREATE INDEX idx_link_validation_run_started_at
    ON link_validation_run(started_at DESC);

CREATE INDEX idx_link_validation_run_item_run_id
    ON link_validation_run_item(run_id);

CREATE INDEX idx_link_validation_run_item_link_id
    ON link_validation_run_item(link_id);
