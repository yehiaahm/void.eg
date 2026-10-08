-- Messages sent from the storefront's Contact page.

CREATE TABLE contact_messages (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(120)  NOT NULL,
  email       VARCHAR(190)  NOT NULL,
  phone       VARCHAR(32)   NULL,                  -- optional, as typed
  message     VARCHAR(2000) NOT NULL,
  lang        VARCHAR(2)    NOT NULL DEFAULT 'en',
  handled     BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX ix_contact_handled (handled, created_at)
);
