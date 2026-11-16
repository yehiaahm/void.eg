-- Duplicate caps (owner, 2026-11-16). On the live store the owner had already added the three caps in
-- Admin -> Products (slugs dark-grey-acid-washed-cap / olive-acid-washed-cap / mustard-acid-washed-cap),
-- so V24 created a second set (void-cap-black / -green / -mustard). Keep the owner's products and
-- remove the migration-made copies, but only where the owner's caps exist and only copies that were
-- never ordered (order history is never deleted). Variants, images and wishlist rows cascade.
-- On a database without the owner's caps (a fresh/dev database) nothing is removed.

DELETE p FROM products p
WHERE p.slug IN ('void-cap-black', 'void-cap-green', 'void-cap-mustard')
  AND (SELECT COUNT(*) FROM (
         SELECT id FROM products
         WHERE slug IN ('dark-grey-acid-washed-cap', 'olive-acid-washed-cap', 'mustard-acid-washed-cap')
       ) owner_caps) > 0
  AND NOT EXISTS (SELECT 1 FROM order_items i WHERE i.product_id = p.id);

-- The owner's caps pointed at uploaded photos that no longer exist on the server (uploads are not kept
-- across redeploys), and the caps are shown only in 3D (no photos on the site). Their front / back
-- slots now hold the still renders of the 3D cap that ship with the site, like the V27 caps.
DELETE i FROM product_images i
JOIN products p ON p.id = i.product_id
WHERE p.slug IN ('dark-grey-acid-washed-cap', 'olive-acid-washed-cap', 'mustard-acid-washed-cap');

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id,
       CONCAT('/assets/products/cap-', c.file, '-', s.kind, '-3d.webp'),
       s.kind, s.pos,
       CONCAT(p.name_en, ', ', s.kind, ' view'),
       CONCAT(p.name_en, '، ', s.side_ar)
FROM products p
JOIN (
  SELECT 'dark-grey-acid-washed-cap' AS slug, 'black' AS file
  UNION ALL SELECT 'olive-acid-washed-cap', 'green'
  UNION ALL SELECT 'mustard-acid-washed-cap', 'mustard'
) c ON c.slug = p.slug
CROSS JOIN (
  SELECT 'front' AS kind, 0 AS pos, 'من الأمام' AS side_ar
  UNION ALL SELECT 'back', 1, 'من الخلف'
) s;
