-- No photos of the caps on the site (owner, 2026-11-16): the caps are shown only as the 3D model. The
-- "front" and "back" slots now hold still renders of that 3D cap (transparent WebP) — they are what the
-- bag, checkout, the card's loading poster and link previews show. The cut-outs and the original photos
-- (hand + background) are taken off the products; the files stay in public/assets/products.

UPDATE product_images i
JOIN products p ON p.id = i.product_id
SET i.url = CONCAT('/assets/products/cap-', SUBSTRING_INDEX(p.slug, '-', -1), '-', i.kind, '-3d.webp'),
    i.alt_en = CONCAT('Acid washed ', LOWER(p.colour_en), ' VOID cap, ', i.kind, ' view'),
    i.alt_ar = CONCAT('كاب VOID أسيد واش ', p.colour_ar, '، ', IF(i.kind = 'front', 'من الأمام', 'من الخلف'))
WHERE p.slug IN ('void-cap-black', 'void-cap-green', 'void-cap-mustard')
  AND i.kind IN ('front', 'back');

DELETE i FROM product_images i
JOIN products p ON p.id = i.product_id
WHERE p.slug IN ('void-cap-black', 'void-cap-green', 'void-cap-mustard')
  AND i.kind NOT IN ('front', 'back');
