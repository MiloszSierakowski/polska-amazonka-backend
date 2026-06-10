UPDATE category c
SET shop_id = s.id
FROM shop s
WHERE c.shop_id IS NULL
  AND (
    LOWER(TRIM(c.name)) = s.slug
    OR LOWER(TRIM(c.name)) = LOWER(TRIM(s.name))
    OR LOWER(TRIM(c.name)) = LOWER(TRIM(s.code))
  )
  AND NOT EXISTS (
    SELECT 1
    FROM category existing
    WHERE existing.shop_id = s.id
  );

WITH base_order AS (
    SELECT COALESCE(MAX(display_order), -1) AS max_display_order
    FROM category
),
missing_shops AS (
    SELECT
        s.id,
        s.name,
        ROW_NUMBER() OVER (ORDER BY s.name ASC, s.id ASC) AS row_number
    FROM shop s
    WHERE NOT EXISTS (
        SELECT 1
        FROM category c
        WHERE c.shop_id = s.id
    )
)
INSERT INTO category (name, image_url, shop_id, display_order)
SELECT
    missing_shops.name,
    NULL,
    missing_shops.id,
    base_order.max_display_order + missing_shops.row_number
FROM missing_shops
CROSS JOIN base_order;
