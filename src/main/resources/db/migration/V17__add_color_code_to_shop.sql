ALTER TABLE shop ADD COLUMN color_code VARCHAR(7);

UPDATE shop SET color_code = '#FF4747' WHERE slug = 'aliexpress';
UPDATE shop SET color_code = '#FB7701' WHERE slug = 'temu';
UPDATE shop SET color_code = '#FF9900' WHERE slug = 'amazon';
UPDATE shop SET color_code = '#FF5A00' WHERE slug = 'allegro';

UPDATE shop SET color_code = '#64748B' WHERE color_code IS NULL;
