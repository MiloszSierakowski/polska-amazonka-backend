UPDATE category
SET image_url = '/uploads/categories/topka.png'
WHERE image_url = '/categories/topka.png';

UPDATE category
SET image_url = '/uploads/categories/temu.png'
WHERE image_url = '/categories/temu.png';

UPDATE category
SET image_url = '/uploads/categories/aliexpress.png'
WHERE image_url = '/categories/aliexpress.png';

UPDATE category
SET image_url = '/uploads/categories/dom.png'
WHERE image_url = '/categories/dom.png';

UPDATE category
SET image_url = '/uploads/categories/znaleziska.png'
WHERE image_url = '/categories/znaleziska.png';

UPDATE category
SET image_url = '/uploads/categories/dzieci.png'
WHERE image_url = '/categories/dzieci.png';

UPDATE category
SET image_url = '/uploads/categories/zwierzeta.png'
WHERE image_url = '/categories/zwierzeta.png';

UPDATE category
SET image_url = REPLACE(image_url, '/categories/', '/uploads/categories/')
WHERE image_url LIKE '/categories/%';
