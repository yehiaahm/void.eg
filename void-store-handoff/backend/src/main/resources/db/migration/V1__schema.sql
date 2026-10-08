-- VOID store schema. Money is stored as integer piastres (EGP x 100).

CREATE TABLE drops (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  number      INT          NOT NULL UNIQUE,
  name_en     VARCHAR(120) NOT NULL,
  name_ar     VARCHAR(120) NOT NULL,
  starts_at   DATETIME(6)  NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'UPCOMING',
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE products (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  slug             VARCHAR(120) NOT NULL UNIQUE,
  drop_id          BIGINT       NULL,
  category_en      VARCHAR(80)  NOT NULL DEFAULT '',
  category_ar      VARCHAR(80)  NOT NULL DEFAULT '',
  name_en          VARCHAR(160) NOT NULL,
  name_ar          VARCHAR(160) NOT NULL,
  description_en   TEXT         NOT NULL,
  description_ar   TEXT         NOT NULL,
  price            INT          NULL,
  compare_at_price INT          NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
  fit_en           VARCHAR(160) NOT NULL DEFAULT '',
  fit_ar           VARCHAR(160) NOT NULL DEFAULT '',
  fabric_en        VARCHAR(255) NOT NULL DEFAULT '',
  fabric_ar        VARCHAR(255) NOT NULL DEFAULT '',
  weight_en        VARCHAR(120) NOT NULL DEFAULT '',
  weight_ar        VARCHAR(120) NOT NULL DEFAULT '',
  colour_en        VARCHAR(120) NOT NULL DEFAULT '',
  colour_ar        VARCHAR(120) NOT NULL DEFAULT '',
  care_en          VARCHAR(500) NOT NULL DEFAULT '',
  care_ar          VARCHAR(500) NOT NULL DEFAULT '',
  size_fit_en      TEXT         NOT NULL,
  size_fit_ar      TEXT         NOT NULL,
  sort_order       INT          NOT NULL DEFAULT 0,
  created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_products_drop FOREIGN KEY (drop_id) REFERENCES drops (id) ON DELETE SET NULL,
  INDEX ix_products_status_sort (status, sort_order)
);

CREATE TABLE product_variants (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id  BIGINT      NOT NULL,
  size        VARCHAR(8)  NOT NULL,
  sku         VARCHAR(64) NULL UNIQUE,
  stock       INT         NOT NULL DEFAULT 0,
  position    INT         NOT NULL DEFAULT 0,
  version     BIGINT      NOT NULL DEFAULT 0,
  CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT uq_variant_size UNIQUE (product_id, size),
  CONSTRAINT ck_variant_stock CHECK (stock >= 0)
);

CREATE TABLE product_images (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id  BIGINT       NOT NULL,
  url         VARCHAR(500) NOT NULL,
  kind        VARCHAR(16)  NOT NULL,
  position    INT          NOT NULL DEFAULT 0,
  alt_en      VARCHAR(255) NOT NULL DEFAULT '',
  alt_ar      VARCHAR(255) NOT NULL DEFAULT '',
  CONSTRAINT fk_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE TABLE users (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  email          VARCHAR(190) NOT NULL UNIQUE,
  password_hash  VARCHAR(100) NOT NULL,
  name           VARCHAR(120) NOT NULL,
  phone          VARCHAR(32)  NULL,
  role           VARCHAR(16)  NOT NULL DEFAULT 'CUSTOMER',
  enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
  last_login_at  DATETIME(6)  NULL,
  created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX ix_users_role (role)
);

CREATE TABLE addresses (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT       NOT NULL,
  name         VARCHAR(120) NOT NULL,
  phone        VARCHAR(32)  NOT NULL,
  governorate  VARCHAR(40)  NOT NULL,
  city         VARCHAR(120) NOT NULL,
  street       VARCHAR(255) NOT NULL,
  building     VARCHAR(120) NOT NULL DEFAULT '',
  notes        VARCHAR(500) NOT NULL DEFAULT '',
  is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  token_hash  CHAR(64)    NOT NULL UNIQUE,
  expires_at  DATETIME(6) NOT NULL,
  revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE password_reset_tokens (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  token_hash  CHAR(64)    NOT NULL UNIQUE,
  expires_at  DATETIME(6) NOT NULL,
  used        BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE shipping_zones (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  code         VARCHAR(40)  NOT NULL UNIQUE,
  name_en      VARCHAR(80)  NOT NULL,
  name_ar      VARCHAR(80)  NOT NULL,
  fee          INT          NULL,
  eta_days     VARCHAR(16)  NOT NULL DEFAULT '',
  enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
  sort_order   INT          NOT NULL DEFAULT 0
);

CREATE TABLE coupons (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  code           VARCHAR(40)  NOT NULL UNIQUE,
  type           VARCHAR(16)  NOT NULL,
  value          INT          NOT NULL DEFAULT 0,
  min_subtotal   INT          NOT NULL DEFAULT 0,
  starts_at      DATETIME(6)  NULL,
  ends_at        DATETIME(6)  NULL,
  max_uses       INT          NULL,
  per_customer   INT          NULL,
  used_count     INT          NOT NULL DEFAULT 0,
  active         BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE orders (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  number           VARCHAR(20)  NOT NULL UNIQUE,
  user_id          BIGINT       NULL,
  lang             VARCHAR(2)   NOT NULL DEFAULT 'en',
  email            VARCHAR(190) NOT NULL,
  name             VARCHAR(120) NOT NULL,
  phone            VARCHAR(32)  NOT NULL,
  governorate      VARCHAR(40)  NOT NULL,
  city             VARCHAR(120) NOT NULL,
  street           VARCHAR(255) NOT NULL,
  building         VARCHAR(120) NOT NULL DEFAULT '',
  address_notes    VARCHAR(500) NOT NULL DEFAULT '',
  customer_note    VARCHAR(1000) NOT NULL DEFAULT '',
  subtotal         INT          NOT NULL,
  discount         INT          NOT NULL DEFAULT 0,
  shipping         INT          NOT NULL DEFAULT 0,
  total            INT          NOT NULL,
  coupon_code      VARCHAR(40)  NULL,
  payment_method   VARCHAR(16)  NOT NULL,
  payment_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  payment_ref      VARCHAR(120) NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT 'NEW',
  created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
  INDEX ix_orders_status_created (status, created_at),
  INDEX ix_orders_created (created_at),
  INDEX ix_orders_phone (phone),
  INDEX ix_orders_email (email)
);

CREATE TABLE order_items (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id      BIGINT       NOT NULL,
  product_id    BIGINT       NULL,
  variant_id    BIGINT       NULL,
  slug          VARCHAR(120) NOT NULL,
  name          VARCHAR(160) NOT NULL,
  size          VARCHAR(8)   NOT NULL,
  sku           VARCHAR(64)  NULL,
  image_url     VARCHAR(500) NULL,
  unit_price    INT          NOT NULL,
  qty           INT          NOT NULL,
  line_total    INT          NOT NULL,
  CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
  CONSTRAINT fk_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL
);

CREATE TABLE order_events (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id    BIGINT        NOT NULL,
  type        VARCHAR(24)   NOT NULL,
  from_status VARCHAR(16)   NULL,
  to_status   VARCHAR(16)   NULL,
  note        VARCHAR(1000) NOT NULL DEFAULT '',
  actor_id    BIGINT        NULL,
  actor_name  VARCHAR(120)  NOT NULL DEFAULT '',
  created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_events_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_events_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE coupon_redemptions (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  coupon_id   BIGINT       NOT NULL,
  order_id    BIGINT       NOT NULL,
  email       VARCHAR(190) NOT NULL,
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE CASCADE,
  CONSTRAINT fk_redemptions_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  INDEX ix_redemptions_email (coupon_id, email)
);

CREATE TABLE settings (
  k           VARCHAR(64)  PRIMARY KEY,
  v           TEXT         NOT NULL,
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE pages (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  slug        VARCHAR(64)  NOT NULL UNIQUE,
  title_en    VARCHAR(160) NOT NULL,
  title_ar    VARCHAR(160) NOT NULL,
  body_en     MEDIUMTEXT   NOT NULL,
  body_ar     MEDIUMTEXT   NOT NULL,
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
