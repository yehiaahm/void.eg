-- The owner's name and price for the white hoodie (2026-11-14): "VOID TRACE_HOODIE WHITE", 899 EGP
-- (stored as piastres). Replaces the working title HOODIE_WHITE; the per-size SKUs
-- (HOODIE_WHITE-S/M/L/XL) are left as they are. The price only fills in a still-empty value, so a
-- price changed later in Admin → Products is never overwritten.

UPDATE products
SET slug = 'void-trace-hoodie-white', name_en = 'VOID TRACE_HOODIE WHITE', name_ar = 'VOID TRACE_HOODIE WHITE'
WHERE slug = 'hoodie-white';

UPDATE products SET price = 89900 WHERE slug = 'void-trace-hoodie-white' AND price IS NULL;
