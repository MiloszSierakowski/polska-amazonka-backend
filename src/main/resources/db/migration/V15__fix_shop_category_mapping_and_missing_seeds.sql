-- Powiąż historyczne kategorie sklepowe (np. temu, aliexpress) z rekordami shop.
UPDATE category c
SET shop_id = s.id
FROM shop s
WHERE c.shop_id IS NULL
  AND LOWER(TRIM(c.name)) = s.slug
  AND NOT EXISTS (
    SELECT 1 FROM category c2 WHERE c2.shop_id = s.id
  );

-- Utwórz brakujące kategorie dla sklepów Amazon i Allegro.
INSERT INTO category (name, image_url, shop_id, display_order)
SELECT s.name, NULL, s.id, 999
FROM shop s
WHERE s.slug IN ('amazon', 'allegro')
  AND NOT EXISTS (
    SELECT 1 FROM category c WHERE c.shop_id = s.id
  )
  AND NOT EXISTS (
    SELECT 1 FROM category c WHERE LOWER(TRIM(c.name)) = s.slug
  );
