-- Shop-grid card for LONGSLEEVE_BLACK (owner request, 2026-11-14): the piece on its own, cut out of
-- the flat-lay photo, shown on a black tile with a white glow (new image slot "cutout", used only on
-- the card; the model photo appears on hover). The product-page gallery is unchanged.

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id, '/assets/products/longsleeve-black-cutout.webp', 'cutout', 4,
       'Black longsleeve with white chest embroidery, shown on its own',
       'لونج سليف أسود بتطريز أبيض على الصدر، القطعة وحدها'
FROM products p
WHERE p.slug = 'longsleeve-black'
  AND NOT EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = p.id AND i.kind = 'cutout');
