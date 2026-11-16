-- VOID CAP in three colours (owner request, 2026-11-15), Drop 01: washed six-panel cap with the outline
-- VOID embroidery in white thread, adjustable strap with a metal slide buckle. The shop shows these
-- products as a rotating 3D cap (frontend `lib/cap.ts` recognises "cap" in the slug / category and the
-- colour word in the slug); the two photos per colour are the real pieces and sit below the 3D viewer.
-- Not invented: PRICE stays NULL ("[PRICE]" on the site, cannot be ordered until the owner sets it in
-- Admin → Products); fabric / weight / care are [PLACEHOLDER]; stock is the usual 10 placeholder.
-- One size (adjustable): a single variant, so the product page shows "One size" instead of S/M/L/XL.

INSERT INTO products (slug, drop_id, category_en, category_ar, name_en, name_ar, description_en, description_ar,
  price, status, fit_en, fit_ar, fabric_en, fabric_ar, weight_en, weight_ar, colour_en, colour_ar, care_en, care_ar,
  size_fit_en, size_fit_ar, sort_order)
SELECT c.slug, d.id, 'Caps', 'كابات', c.name_en, c.name_ar,
  CONCAT('A washed six-panel cap in ', LOWER(c.colour_en), ' with the outline VOID logo embroidered in white thread on the front. Curved stitched brim, embroidered eyelets and a covered button. Adjustable strap with a metal slide buckle. Part of Drop 01 — limited run.'),
  CONCAT('كاب مغسول بستة قطع بلون ', c.colour_ar, ' مع لوجو VOID المفرّغ مطرّزًا بخيط أبيض على الأمام. حافة منحنية بغرز متعددة وفتحات تهوية مطرّزة وزرار مغطّى. حزام خلفي قابل للتعديل بإبزيم معدني. جزء من دروب 01 — كمية محدودة.'),
  NULL, 'ACTIVE', 'Adjustable, one size', 'مقاس واحد قابل للتعديل', '[FABRIC]', '[الخامة]', '[GSM]', '[GSM]', c.colour_en, c.colour_ar,
  '[CARE INSTRUCTIONS]', '[تعليمات العناية]',
  'One size, adjustable strap.\n[ADD HEAD CIRCUMFERENCE RANGE IN CM]', 'مقاس واحد بحزام قابل للتعديل.\n[أضف محيط الرأس بالسنتيمتر]',
  (SELECT COALESCE(MAX(sort_order), 0) FROM products) + c.pos
FROM drops d
CROSS JOIN (
  SELECT 'void-cap-black' AS slug, 'VOID CAP BLACK' AS name_en, 'VOID CAP BLACK' AS name_ar, 'Black' AS colour_en, 'أسود' AS colour_ar, 1 AS pos
  UNION ALL SELECT 'void-cap-green', 'VOID CAP GREEN', 'VOID CAP GREEN', 'Green', 'أخضر', 2
  UNION ALL SELECT 'void-cap-mustard', 'VOID CAP MUSTARD', 'VOID CAP MUSTARD', 'Mustard', 'خردلي', 3
) c
WHERE d.number = 1;

INSERT INTO product_variants (product_id, size, sku, stock, position)
SELECT p.id, 'M', CONCAT('CAP_', UPPER(SUBSTRING_INDEX(p.slug, '-', -1)), '-ONE'), 10, 0
FROM products p
WHERE p.slug IN ('void-cap-black', 'void-cap-green', 'void-cap-mustard');

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id, i.url, i.kind, i.pos, i.alt_en, i.alt_ar
FROM products p
JOIN (
  SELECT 'void-cap-black' AS slug, '/assets/products/cap-black-front.webp' AS url, 'front' AS kind, 0 AS pos,
         'Washed black VOID cap, front, with the white outline VOID embroidery' AS alt_en,
         'كاب VOID أسود مغسول من الأمام بتطريز VOID الأبيض المفرّغ' AS alt_ar
  UNION ALL SELECT 'void-cap-black', '/assets/products/cap-black-back.webp', 'back', 1,
         'Washed black VOID cap, back, with the strap and metal slide buckle',
         'كاب VOID أسود مغسول من الخلف مع الحزام والإبزيم المعدني'
  UNION ALL SELECT 'void-cap-green', '/assets/products/cap-green-front.webp', 'front', 0,
         'Washed green VOID cap, front, with the white outline VOID embroidery',
         'كاب VOID أخضر مغسول من الأمام بتطريز VOID الأبيض المفرّغ'
  UNION ALL SELECT 'void-cap-green', '/assets/products/cap-green-back.webp', 'back', 1,
         'Washed green VOID cap, back, with the strap and metal slide buckle',
         'كاب VOID أخضر مغسول من الخلف مع الحزام والإبزيم المعدني'
  UNION ALL SELECT 'void-cap-mustard', '/assets/products/cap-mustard-front.webp', 'front', 0,
         'Washed mustard VOID cap, front, with the white outline VOID embroidery',
         'كاب VOID خردلي مغسول من الأمام بتطريز VOID الأبيض المفرّغ'
  UNION ALL SELECT 'void-cap-mustard', '/assets/products/cap-mustard-back.webp', 'back', 1,
         'Washed mustard VOID cap, back, with the strap and metal slide buckle',
         'كاب VOID خردلي مغسول من الخلف مع الحزام والإبزيم المعدني'
) i ON i.slug = p.slug;
