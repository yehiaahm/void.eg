-- Shop-grid cards show the model photos again instead of the cut-out pieces (owner request,
-- 2026-11-14). The owner picked the card photo for each piece:
--   LONGSLEEVE_BLACK      -> the beanie / clear-glasses shot becomes the front image; the earphones
--                            shot that was the front moves to the hover slot ("back")
--   VOID TRACE_HOODIE WHITE -> a new front photo (hold-the-hoodie shot, re-cropped)
--   VOID TRACE_HOODIE NAVY  -> unchanged (its front photo already is the chosen one)
-- The cut-out rows (V15, V16, V18) are removed; the "cutout" image slot itself stays available in the
-- admin for any piece that should get the black-tile + glow card again.

DELETE FROM product_images WHERE kind = 'cutout';

-- LONGSLEEVE_BLACK: drop the old hover photo (the beanie shot is now the front), demote the old front.
DELETE i FROM product_images i JOIN products p ON p.id = i.product_id
WHERE p.slug = 'longsleeve-black' AND i.kind = 'back';

UPDATE product_images i JOIN products p ON p.id = i.product_id
SET i.kind = 'back', i.position = 1
WHERE p.slug = 'longsleeve-black' AND i.kind = 'front';

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id, '/assets/products/longsleeve-black-front-v2.webp', 'front', 0,
       'Black longsleeve with white chest embroidery, worn with a patterned beanie and clear-lens glasses',
       'لونج سليف أسود بتطريز أبيض على الصدر، مع بينية منقوشة ونظارة شفافة'
FROM products p WHERE p.slug = 'longsleeve-black';

-- VOID TRACE_HOODIE WHITE: swap the front photo.
UPDATE product_images i JOIN products p ON p.id = i.product_id
SET i.url = '/assets/products/hoodie-white-front-v2.webp'
WHERE p.slug = 'void-trace-hoodie-white' AND i.kind = 'front';
