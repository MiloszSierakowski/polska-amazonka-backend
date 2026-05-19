CREATE TABLE changelog (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_changelog_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
