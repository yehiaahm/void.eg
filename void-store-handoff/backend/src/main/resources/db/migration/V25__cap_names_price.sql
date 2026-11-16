-- Real names and price for the three caps (owner, 2026-11-15): 399 EGP each (stored as piastres).
--   void-cap-black   -> DARK GREY ACID WASHED CAP
--   void-cap-mustard -> MUSTARD ACID WASHED CAP
--   void-cap-green   -> OLIVE ACID WASHED CAP
-- The slugs stay as they are (links, wishlists and the 3D colour lookup use them).

UPDATE products p
JOIN (
  SELECT 'void-cap-black' AS slug, 'DARK GREY ACID WASHED CAP' AS name_en, 'Dark grey' AS colour_en, 'رمادي غامق' AS colour_ar
  UNION ALL SELECT 'void-cap-mustard', 'MUSTARD ACID WASHED CAP', 'Mustard', 'خردلي'
  UNION ALL SELECT 'void-cap-green', 'OLIVE ACID WASHED CAP', 'Olive', 'زيتي'
) c ON c.slug = p.slug
SET p.name_en = c.name_en,
    p.name_ar = c.name_en,
    p.price = 39900,
    p.colour_en = c.colour_en,
    p.colour_ar = c.colour_ar,
    p.description_en = CONCAT('An acid washed six-panel cap in ', LOWER(c.colour_en), ' with the outline VOID logo embroidered in white thread on the front. Curved stitched brim, embroidered eyelets and a covered button. Adjustable strap with a metal slide buckle. Part of Drop 01 — limited run.'),
    p.description_ar = CONCAT('كاب أسيد واش بستة قطع بلون ', c.colour_ar, ' مع لوجو VOID المفرّغ مطرّزًا بخيط أبيض على الأمام. حافة منحنية بغرز متعددة وفتحات تهوية مطرّزة وزرار مغطّى. حزام خلفي قابل للتعديل بإبزيم معدني. جزء من دروب 01 — كمية محدودة.');

-- Photo descriptions follow the new colour names.
UPDATE product_images i
JOIN products p ON p.id = i.product_id
SET i.alt_en = CONCAT('Acid washed ', LOWER(p.colour_en), ' VOID cap, ', IF(i.kind = 'front', 'front, with the white outline VOID embroidery', 'back, with the strap and metal slide buckle')),
    i.alt_ar = CONCAT('كاب VOID أسيد واش ', p.colour_ar, ' ', IF(i.kind = 'front', 'من الأمام بتطريز VOID الأبيض المفرّغ', 'من الخلف مع الحزام والإبزيم المعدني'))
WHERE p.slug IN ('void-cap-black', 'void-cap-green', 'void-cap-mustard');
