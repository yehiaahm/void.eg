-- Caps turn as their real photos instead of a 3D model (owner request, 2026-11-15). The "front" and "back"
-- slots now hold the caps cut out of the owner's photos (transparent WebP, hand and background removed) —
-- the shop card and the product-page viewer turn between them. The original photos stay on the product
-- page gallery as the "detail" (front) and "body" (back) slots.

UPDATE product_images i
JOIN products p ON p.id = i.product_id
SET i.url = CONCAT('/assets/products/cap-', SUBSTRING_INDEX(p.slug, '-', -1), '-', i.kind, '-cut.webp')
WHERE p.slug IN ('void-cap-black', 'void-cap-green', 'void-cap-mustard')
  AND i.kind IN ('front', 'back');

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id,
       CONCAT('/assets/products/cap-', SUBSTRING_INDEX(p.slug, '-', -1), '-', s.side, '.webp'),
       s.kind, s.pos,
       CONCAT('Acid washed ', LOWER(p.colour_en), ' VOID cap, photo of the ', s.side),
       CONCAT('كاب VOID أسيد واش ', p.colour_ar, '، صورة ', s.side_ar)
FROM products p
CROSS JOIN (
  SELECT 'front' AS side, 'detail' AS kind, 2 AS pos, 'من الأمام' AS side_ar
  UNION ALL SELECT 'back', 'body', 3, 'من الخلف'
) s
WHERE p.slug IN ('void-cap-black', 'void-cap-green', 'void-cap-mustard');
