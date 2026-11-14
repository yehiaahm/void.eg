-- Real size runs (owner, 2026-11-14):
--   both hoodies (white, navy): S, M, L   -> XL is removed
--   LONGSLEEVE_BLACK:           M, L, XL  -> S is removed
-- A size that was ever ordered is not deleted (the order history points at it); its stock is set to 0
-- so it shows as sold out instead.

UPDATE product_variants v
JOIN products p ON p.id = v.product_id
SET v.stock = 0
WHERE ((p.slug IN ('void-trace-hoodie-white', 'void-trace-hoodie-navy') AND v.size = 'XL')
    OR (p.slug = 'longsleeve-black' AND v.size = 'S'))
  AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.variant_id = v.id);

DELETE v FROM product_variants v
JOIN products p ON p.id = v.product_id
WHERE ((p.slug IN ('void-trace-hoodie-white', 'void-trace-hoodie-navy') AND v.size = 'XL')
    OR (p.slug = 'longsleeve-black' AND v.size = 'S'))
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.variant_id = v.id);
