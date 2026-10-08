-- Wishlist + return / exchange requests.

CREATE TABLE wishlist_items (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  product_id  BIGINT      NOT NULL,
  created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_wish_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_wish_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT uq_wish UNIQUE (user_id, product_id)
);

-- How many pieces of each order line came back (via return requests or a full return),
-- so stock is never restocked twice.
ALTER TABLE order_items ADD COLUMN returned_qty INT NOT NULL DEFAULT 0;

CREATE TABLE return_requests (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  number         VARCHAR(24)   NOT NULL UNIQUE,
  order_id       BIGINT        NOT NULL,
  type           VARCHAR(16)   NOT NULL,              -- RETURN | EXCHANGE
  status         VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
  reason         VARCHAR(24)   NOT NULL,
  customer_note  VARCHAR(1000) NOT NULL DEFAULT '',
  refund_amount  INT           NULL,
  created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ret_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  INDEX ix_ret_status (status, created_at)
);

CREATE TABLE return_items (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id       BIGINT     NOT NULL,
  order_item_id    BIGINT     NOT NULL,
  qty              INT        NOT NULL,
  exchange_size    VARCHAR(8) NULL,
  CONSTRAINT fk_reti_request FOREIGN KEY (request_id) REFERENCES return_requests (id) ON DELETE CASCADE,
  CONSTRAINT fk_reti_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE
);

CREATE TABLE return_events (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id  BIGINT        NOT NULL,
  from_status VARCHAR(16)   NULL,
  to_status   VARCHAR(16)   NOT NULL,
  note        VARCHAR(1000) NOT NULL DEFAULT '',
  actor_name  VARCHAR(120)  NOT NULL DEFAULT '',
  created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_rete_request FOREIGN KEY (request_id) REFERENCES return_requests (id) ON DELETE CASCADE
);

-- Default window for return / exchange requests after delivery (owner can change it in Admin → Content).
INSERT INTO settings (k, v) VALUES ('return_window_days', '14');
