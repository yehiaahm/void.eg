-- Owner data for the Vision Sweatshirt (2026-11-14): price 799 EGP (stored as piastres) and SKU
-- LONGSLEEVE_BLACK. SKUs are unique per size, so each size gets the code with its size suffix.
-- Only fills in what is still empty, so values changed later in Admin → Products are never overwritten.

UPDATE products SET price = 79900 WHERE slug = 'vision-sweatshirt' AND price IS NULL;

UPDATE product_variants v
JOIN products p ON p.id = v.product_id
SET v.sku = CONCAT('LONGSLEEVE_BLACK-', v.size)
WHERE p.slug = 'vision-sweatshirt' AND v.sku IS NULL;
