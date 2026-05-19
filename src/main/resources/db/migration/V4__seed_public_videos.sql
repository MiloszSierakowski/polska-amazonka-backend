INSERT INTO link (url, type, is_active, last_checked_at)
SELECT 'https://www.temu.com/pl/8-sztuk--regulowane-plastikowe-haczyki-na-kubki-oszcz%C4%99dzaj%C4%85ce-miejsce-rozszerzalne-stojaki-do-uk%C5%82adania-kubk%C3%B3w-do-kawy-i-herbaty-idealne-do-przechowywania-w-szafce-kuchennej-haczyki-w--casual-do-organizowania-kubk%C3%B3w-stojak-do-przechowywania-kubk%C3%B3w-g-601100659937349.html', 'product', TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM link WHERE url LIKE 'https://www.temu.com/pl/8-sztuk--regulowane-plastikowe-haczyki%');

INSERT INTO link (url, type, is_active, last_checked_at)
SELECT 'https://www.temu.com/pl/6-szt-uniwersalne-magnetyczne-wiert%C5%82a-do-%C5%9Brub-metalowe-magnetyczne-%C5%9Bruby-wymienny-klucz-p%C5%82aski-i-wiertarka-elektryczna-1-4-cala-6--pasuj%C4%85ce-do-wkr%C4%99tak%C3%B3w-phillips-d%C5%82ugie-wiert%C5%82a-g-601105023180435.html', 'product', TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM link WHERE url LIKE 'https://www.temu.com/pl/6-szt-uniwersalne-magnetyczne-wiert%');

INSERT INTO link (url, type, is_active, last_checked_at)
SELECT 'https://www.temu.com/pl/kompaktowa-stalowa-kompaktowa-lataj%C4%85ca-ulewa-czterobiegowa-obrotowa-dysza-zmywanie-naczy%C5%84-domowy--wodospadowy-ma%C5%82a-bateria-prysznicowa-z-rowkiem-do-mycia-warzyw-niezb%C4%99dnik-do-u%C5%BCytku-domowego-g-601103574565059.html', 'product', TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM link WHERE url LIKE 'https://www.temu.com/pl/kompaktowa-stalowa-kompaktowa-lataj%');

INSERT INTO link (url, type, is_active, last_checked_at)
SELECT 'https://www.temu.com/pl/stolik-kawowy-z-podnoszonym--stolik-do-salonu-z-szufladami-ukrytymi-przegrodami-i-otwart%C4%85-p%C3%B3%C5%82k%C4%85-g-601101412727681.html', 'product', TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM link WHERE url LIKE 'https://www.temu.com/pl/stolik-kawowy-z-podnoszonym%');

INSERT INTO link (url, type, is_active, last_checked_at)
SELECT 'https://www.temu.com/pl/3-lampki-akumulatorowe-lampki-nocne-2-szt-podw%C3%B3jna-g%C5%82owica-led-montowana-na-%C5%9Bcianie-z-czujnikiem-%C5%9Bciemnialne-3-kolory-ciep%C5%82a-biel-neutralna-zimna-biel-%C5%82adowanie-do-salonu-sypialni-korytarza-o%C5%9Bwietlenie-na-11-%C5%9Bwi%C4%85t-g-601103408935241.html', 'product', TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM link WHERE url LIKE 'https://www.temu.com/pl/3-lampki-akumulatorowe-lampki-nocne%');

INSERT INTO product (name, image_url, product_link_id)
SELECT 'Regulowane haczyki na kubki', 'https://img.kwcdn.com/product/fancy/60f70492-8d28-4688-9cac-567e5e5a6724.jpg', l.id
FROM link l
WHERE l.url LIKE 'https://www.temu.com/pl/8-sztuk--regulowane-plastikowe-haczyki%'
AND NOT EXISTS (SELECT 1 FROM product WHERE name = 'Regulowane haczyki na kubki');

INSERT INTO product (name, image_url, product_link_id)
SELECT 'Magnetyczne bity do śrub', 'https://img.kwcdn.com/product/fancy/f5a68220-4472-4fdf-b76b-722e947b6524.jpg', l.id
FROM link l
WHERE l.url LIKE 'https://www.temu.com/pl/6-szt-uniwersalne-magnetyczne-wiert%'
AND NOT EXISTS (SELECT 1 FROM product WHERE name = 'Magnetyczne bity do śrub');

INSERT INTO product (name, image_url, product_link_id)
SELECT 'Obrotowa dysza do kranu', 'https://img.kwcdn.com/product/fancy/97f50a72-626f-48d8-aac2-7c5ae631fc51.jpg', l.id
FROM link l
WHERE l.url LIKE 'https://www.temu.com/pl/kompaktowa-stalowa-kompaktowa-lataj%'
AND NOT EXISTS (SELECT 1 FROM product WHERE name = 'Obrotowa dysza do kranu');

INSERT INTO product (name, image_url, product_link_id)
SELECT 'Stolik kawowy z podnoszonym blatem', 'https://img.kwcdn.com/product/fancy/2a3e78bc-00df-4f13-8dc9-0054ff0b1524.jpg', l.id
FROM link l
WHERE l.url LIKE 'https://www.temu.com/pl/stolik-kawowy-z-podnoszonym%'
AND NOT EXISTS (SELECT 1 FROM product WHERE name = 'Stolik kawowy z podnoszonym blatem');

INSERT INTO product (name, image_url, product_link_id)
SELECT 'Lampki akumulatorowe LED', 'https://img.kwcdn.com/product/fancy/7634f292-754c-47fa-84ae-b6ec3ce49b25.jpg', l.id
FROM link l
WHERE l.url LIKE 'https://www.temu.com/pl/3-lampki-akumulatorowe-lampki-nocne%'
AND NOT EXISTS (SELECT 1 FROM product WHERE name = 'Lampki akumulatorowe LED');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@sars_m1/video/7617920310918106400', 'https://placehold.co/720x1280/2196f3/ffffff?text=TOPKA+1', 'TikTok #1 - topka', TRUE, '2026-03-15T18:30:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617920310918106400');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@sars_m1/video/7617920712510147873', 'https://placehold.co/720x1280/ff9800/ffffff?text=TEMU+1', 'TikTok #2 - temu', TRUE, '2026-03-14T15:10:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617920712510147873');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@sars_m1/video/7617921149745335584', 'https://placehold.co/720x1280/2196f3/ffffff?text=TOPKA+2', 'TikTok #3 - topka', TRUE, '2026-03-13T12:45:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617921149745335584');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@sars_m1/video/7617921944654056736', 'https://placehold.co/720x1280/ff9800/ffffff?text=TEMU+2', 'TikTok #4 - temu', TRUE, '2026-03-12T20:00:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617921944654056736');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@sars_m1/video/7617922170529893664', 'https://placehold.co/720x1280/673ab7/ffffff?text=TOPKA%2BTEMU', 'TikTok #5 - topka + temu', TRUE, '2026-03-11T09:30:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922170529893664');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@sars_m1/video/7617922651893386529', 'https://placehold.co/720x1280/2196f3/ffffff?text=TOPKA+3', 'TikTok #6 - topka', TRUE, '2026-03-10T16:20:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922651893386529');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@sars_m1/video/7617923493534928161', 'https://placehold.co/720x1280/ff9800/ffffff?text=TEMU+3', 'TikTok #7 - temu', TRUE, '2026-03-09T11:05:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617923493534928161');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@polskaamazonka/video/7566964823578266902', NULL, 'PA publiczny film #1', TRUE, '2026-03-16T10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@polskaamazonka/video/7566964823578266902');

INSERT INTO video (tiktok_url, preview_image_url, title, is_active, created_at)
SELECT 'https://www.tiktok.com/@polskaamazonka/video/7551410528862702870', NULL, 'PA publiczny film #2', TRUE, '2026-03-16T09:30:00'
WHERE NOT EXISTS (SELECT 1 FROM video WHERE tiktok_url = 'https://www.tiktok.com/@polskaamazonka/video/7551410528862702870');

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617920310918106400' AND c.name = 'topka'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617920712510147873' AND c.name = 'temu'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617921149745335584' AND c.name = 'topka'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617921944654056736' AND c.name = 'temu'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922170529893664' AND c.name = 'topka'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922170529893664' AND c.name = 'temu'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922651893386529' AND c.name = 'topka'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617923493534928161' AND c.name = 'temu'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@polskaamazonka/video/7566964823578266902' AND c.name = 'topka'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videocategory (video_id, category_id)
SELECT v.id, c.id FROM video v, category c
WHERE v.tiktok_url = 'https://www.tiktok.com/@polskaamazonka/video/7551410528862702870' AND c.name = 'temu'
AND NOT EXISTS (SELECT 1 FROM videocategory vc WHERE vc.video_id = v.id AND vc.category_id = c.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617920310918106400' AND p.name = 'Regulowane haczyki na kubki'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617920712510147873' AND p.name = 'Magnetyczne bity do śrub'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617920712510147873' AND p.name = 'Obrotowa dysza do kranu'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617921149745335584' AND p.name = 'Stolik kawowy z podnoszonym blatem'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617921944654056736' AND p.name = 'Lampki akumulatorowe LED'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922170529893664' AND p.name = 'Regulowane haczyki na kubki'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922170529893664' AND p.name = 'Lampki akumulatorowe LED'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922651893386529' AND p.name = 'Magnetyczne bity do śrub'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922651893386529' AND p.name = 'Obrotowa dysza do kranu'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617922651893386529' AND p.name = 'Stolik kawowy z podnoszonym blatem'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617923493534928161' AND p.name = 'Lampki akumulatorowe LED'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@sars_m1/video/7617923493534928161' AND p.name = 'Regulowane haczyki na kubki'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@polskaamazonka/video/7566964823578266902' AND p.name = 'Regulowane haczyki na kubki'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@polskaamazonka/video/7566964823578266902' AND p.name = 'Obrotowa dysza do kranu'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);

INSERT INTO videoproduct (video_id, product_id)
SELECT v.id, p.id FROM video v, product p
WHERE v.tiktok_url = 'https://www.tiktok.com/@polskaamazonka/video/7551410528862702870' AND p.name = 'Lampki akumulatorowe LED'
AND NOT EXISTS (SELECT 1 FROM videoproduct vp WHERE vp.video_id = v.id AND vp.product_id = p.id);
