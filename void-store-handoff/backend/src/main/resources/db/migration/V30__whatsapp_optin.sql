-- Customers can opt in to WhatsApp order updates at checkout (WhatsApp requires an explicit opt-in).
-- Nothing is ever sent unless the owner also switches WhatsApp on and configures it (docs/DEPLOY.md).
ALTER TABLE orders ADD COLUMN whatsapp_optin BOOLEAN NOT NULL DEFAULT FALSE;
