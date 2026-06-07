ALTER TABLE category ADD COLUMN display_order BIGINT NOT NULL DEFAULT 0;

WITH ordered AS (
    SELECT id, (ROW_NUMBER() OVER (ORDER BY id ASC) - 1) AS ord
    FROM category
)
UPDATE category c
SET display_order = ordered.ord
FROM ordered
WHERE c.id = ordered.id;
