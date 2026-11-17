-- The exact spot a customer shared with "Use my location" at checkout (2026-11-17), so the courier can open it in a
-- map app. NULL for orders where the address was typed. Staff-only: never returned on the customer / tracking API.
ALTER TABLE orders
  ADD COLUMN pin_lat DOUBLE NULL,
  ADD COLUMN pin_lng DOUBLE NULL;
