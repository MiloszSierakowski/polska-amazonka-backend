CREATE TABLE shop (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    shop_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE
);

INSERT INTO shop (slug, code, name, shop_url, is_active) VALUES
    ('aliexpress', 'ALIEXPRESS', 'AliExpress', 'https://pl.aliexpress.com', TRUE),
    ('temu', 'TEMU', 'Temu', 'https://www.temu.com', TRUE),
    ('amazon', 'AMAZON', 'Amazon', 'https://www.amazon.pl', TRUE),
    ('allegro', 'ALLEGRO', 'Allegro', 'https://allegro.pl', TRUE);
