-- Vision Sweatshirt (owner request, 2026-11-14), Drop 01.
-- Photos live in the frontend's public/assets/products/ (served as /assets/products/…); the four
-- slots follow the product-page gallery: front, back (second look, shown on card hover), detail, body.
-- Not invented: the price is NULL (orders are refused with no_price until it is set in Admin → Products)
-- and fabric / weight / care / size chart stay as [PLACEHOLDER] values, like the other Drop 01 pieces.
-- Stock is the same 10-per-size placeholder as V2 until real quantities are entered in the admin.

INSERT INTO products (slug, drop_id, category_en, category_ar, name_en, name_ar, description_en, description_ar,
  price, status, fit_en, fit_ar, fabric_en, fabric_ar, weight_en, weight_ar, colour_en, colour_ar, care_en, care_ar,
  size_fit_en, size_fit_ar, sort_order)
SELECT 'vision-sweatshirt', d.id, 'Sweatshirts', 'سويتشيرتات', 'Vision Sweatshirt', 'Vision Sweatshirt',
  'A boxy, oversized crewneck with dropped shoulders, a ribbed neck and raw-cut hem and cuffs. “Started with nothing but a vision” is embroidered in white across the chest. Part of Drop 01 — limited run.',
  'سويتشيرت أوفرسايز بقصّة بوكسي وأكتاف منسدلة وياقة دائرية مضلّعة، مع حواف سفلية وأساور بقصّة خام. عبارة «Started with nothing but a vision» مطرّزة بالأبيض على الصدر. جزء من دروب 01 — كمية محدودة.',
  NULL, 'ACTIVE', 'Oversized', 'أوفرسايز', '[FABRIC]', '[الخامة]', '[GSM]', '[GSM]', 'Black', 'أسود',
  '[CARE INSTRUCTIONS]', '[تعليمات العناية]',
  '[ADD MEASUREMENTS IN CM]\nModel is [HEIGHT] and wears size [SIZE].', '[أضف المقاسات بالسنتيمتر]\nطول الموديل [الطول] ويرتدي مقاس [المقاس].',
  (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM products)
FROM drops d WHERE d.number = 1;

INSERT INTO product_variants (product_id, size, stock, position)
SELECT p.id, s.size, 10, s.pos
FROM products p
CROSS JOIN (SELECT 'S' AS size, 0 AS pos UNION ALL SELECT 'M', 1 UNION ALL SELECT 'L', 2 UNION ALL SELECT 'XL', 3) s
WHERE p.slug = 'vision-sweatshirt';

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id, i.url, i.kind, i.pos, i.alt_en, i.alt_ar
FROM products p
CROSS JOIN (
  SELECT '/assets/products/vision-sweatshirt-front.webp' AS url, 'front' AS kind, 0 AS pos,
         'Vision Sweatshirt in black, worn with light-wash jeans, front view' AS alt_en,
         'سويتشيرت Vision أسود مع جينز فاتح، من الأمام' AS alt_ar
  UNION ALL SELECT '/assets/products/vision-sweatshirt-back.webp', 'back', 1,
         'Vision Sweatshirt in black, styled with a patterned beanie and clear-lens glasses',
         'سويتشيرت Vision أسود مع بينية منقوشة ونظارة شفافة'
  UNION ALL SELECT '/assets/products/vision-sweatshirt-detail.webp', 'detail', 2,
         'Vision Sweatshirt laid flat, showing the white chest embroidery and raw-cut hem',
         'سويتشيرت Vision مفرود، يظهر التطريز الأبيض على الصدر والحافة الخام'
  UNION ALL SELECT '/assets/products/vision-sweatshirt-body.webp', 'body', 3,
         'Vision Sweatshirt in black worn with a VOID cap and plaid trousers',
         'سويتشيرت Vision أسود مع كاب VOID وبنطلون كاروهات'
) i
WHERE p.slug = 'vision-sweatshirt';
