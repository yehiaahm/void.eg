-- Shop-grid card for HOODIE_WHITE: the piece on its own (cut out of the flat-lay photo), shown on a
-- black tile with a white glow, like LONGSLEEVE_BLACK (V15). The model photo appears on hover.

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id, '/assets/products/hoodie-white-cutout.webp', 'cutout', 4,
       'White hoodie with black Arabic lettering on the chest, shown on its own',
       'هودي أبيض بخط عربي أسود على الصدر، القطعة وحدها'
FROM products p
WHERE p.slug = 'hoodie-white'
  AND NOT EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = p.id AND i.kind = 'cutout');
