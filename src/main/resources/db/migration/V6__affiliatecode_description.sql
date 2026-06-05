ALTER TABLE affiliatecode ADD COLUMN IF NOT EXISTS description VARCHAR(500);

UPDATE affiliatecode
SET description = '⭐ 20zł zniżki przy zakupach za 200 zł ⭐'
WHERE platform = 'ALIEXPRESS' AND type = 'DISCOUNT' AND (description IS NULL OR description = '');

UPDATE affiliatecode
SET description = '⭐ Nowi użytkownicy – 400zł kuponów ⭐'
WHERE platform = 'TEMU' AND type = 'DISCOUNT' AND (description IS NULL OR description = '');
