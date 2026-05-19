CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    CONSTRAINT users_role_chk CHECK (role IN ('ADMIN', 'WORKER'))
);

CREATE TABLE category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    image_url VARCHAR(500)
);

CREATE TABLE video (
    id BIGSERIAL PRIMARY KEY,
    tiktok_url VARCHAR(500),
    local_mp4_url VARCHAR(500),
    preview_image_url VARCHAR(500),
    title VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    last_checked_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE link (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_checked_at TIMESTAMP
);

CREATE TABLE videocategory (
    id BIGSERIAL PRIMARY KEY,
    video_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT fk_videocategory_video FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE,
    CONSTRAINT fk_videocategory_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE,
    CONSTRAINT uq_videocategory_video_category UNIQUE (video_id, category_id)
);

CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    image_url VARCHAR(500),
    product_link_id BIGINT NOT NULL,
    CONSTRAINT fk_product_link FOREIGN KEY (product_link_id) REFERENCES link (id) ON DELETE CASCADE
);

CREATE TABLE videoproduct (
    id BIGSERIAL PRIMARY KEY,
    video_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT fk_videoproduct_video FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE,
    CONSTRAINT fk_videoproduct_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT uq_videoproduct_video_product UNIQUE (video_id, product_id)
);

CREATE TABLE affiliatecode (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(50) NOT NULL,
    code_value VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT affiliatecode_type_chk CHECK (type IN ('AFFILIATE', 'DISCOUNT'))
);

CREATE TABLE linkcheckhistory (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL,
    status_code INTEGER NOT NULL,
    checked_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_linkcheckhistory_link FOREIGN KEY (link_id) REFERENCES link (id) ON DELETE CASCADE
);

CREATE TABLE clickstat (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    clicked_at TIMESTAMP DEFAULT NOW()
);
