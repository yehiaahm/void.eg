-- Security hardening (audit findings VOID-05, PV-1, PV-4).

-- PV-1: optimistic lock on orders, a backstop for the pessimistic row lock taken before stock is restocked.
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- VOID-05: a per-customer coupon limit must not depend on a typed e-mail alone. Redemptions now also remember the
-- phone, the signed-in account and a hash of the delivery address, and the limit counts any of them.
ALTER TABLE coupon_redemptions
  ADD COLUMN phone       VARCHAR(32) NULL,
  ADD COLUMN user_id     BIGINT      NULL,
  ADD COLUMN address_key VARCHAR(64) NULL;

UPDATE coupon_redemptions r
  JOIN orders o ON o.id = r.order_id
   SET r.phone = o.phone, r.user_id = o.user_id;

CREATE INDEX ix_redemptions_phone   ON coupon_redemptions (coupon_id, phone);
CREATE INDEX ix_redemptions_user    ON coupon_redemptions (coupon_id, user_id);
CREATE INDEX ix_redemptions_address ON coupon_redemptions (coupon_id, address_key);

-- PV-4: cash-on-delivery orders reserve stock before anyone has confirmed them. Orders nobody confirmed within this
-- many hours are cancelled automatically and the stock goes back on the shelf. 0 switches it off.
INSERT IGNORE INTO settings (k, v) VALUES ('unconfirmed_order_hours', '72');
