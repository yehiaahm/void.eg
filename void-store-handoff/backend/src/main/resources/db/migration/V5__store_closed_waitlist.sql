-- "Close the store" switch + the waitlist visitors fill in while it is closed.

-- Open by default. The message is optional; empty = the storefront's default copy.
INSERT INTO settings (k, v) VALUES
  ('store_closed', 'false'),
  ('closed_message_en', ''),
  ('closed_message_ar', '');

CREATE TABLE waitlist_entries (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(120)  NOT NULL,
  phone       VARCHAR(16)   NOT NULL,              -- normalised 01XXXXXXXXX
  email       VARCHAR(190)  NULL,                  -- optional
  notes       VARCHAR(1000) NOT NULL DEFAULT '',
  lang        VARCHAR(2)    NOT NULL DEFAULT 'en',
  contacted   BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX ix_waitlist_contacted (contacted, created_at),
  INDEX ix_waitlist_phone (phone)
);
