ALTER TABLE affiliatecode ADD COLUMN display_order BIGINT NOT NULL DEFAULT 0;

WITH ordered AS (
    SELECT id, (ROW_NUMBER() OVER (PARTITION BY type ORDER BY id ASC) - 1) AS ord
    FROM affiliatecode
)
UPDATE affiliatecode ac
SET display_order = ordered.ord
FROM ordered
WHERE ac.id = ordered.id;
