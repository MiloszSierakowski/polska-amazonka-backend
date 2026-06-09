CREATE TABLE navigation_link (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    icon VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order BIGINT NOT NULL DEFAULT 0
);
