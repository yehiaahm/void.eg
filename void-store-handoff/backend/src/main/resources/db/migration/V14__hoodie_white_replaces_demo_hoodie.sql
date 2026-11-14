-- HOODIE_WHITE (owner request, 2026-11-14): the first real hoodie replaces the demo "Event Horizon
-- Hoodie" from the V2 seed. The chest text reads "ابتسامات عامة / نوايا خاصة" ("Public smiles /
-- Private intentions").
-- Not invented: the price is NULL (orders are refused with no_price until it is set in Admin →
-- Products), fabric / weight / care / size chart stay [PLACEHOLDER] and stock is the usual 10-per-size
-- placeholder. Photos: frontend/public/assets/products/hoodie-white-*.webp.

INSERT INTO products (slug, drop_id, category_en, category_ar, name_en, name_ar, description_en, description_ar,
  price, status, fit_en, fit_ar, fabric_en, fabric_ar, weight_en, weight_ar, colour_en, colour_ar, care_en, care_ar,
  size_fit_en, size_fit_ar, sort_order)
SELECT 'hoodie-white', d.id, 'Hoodies', 'هوديز', 'HOODIE_WHITE', 'HOODIE_WHITE',
  'An oversized hoodie with dropped shoulders, a deep hood and ribbed cuffs and hem. “Public smiles / Private intentions” is set across the chest in black Arabic lettering. Part of Drop 01 — limited run.',
  'هودي أوفرسايز بأكتاف منسدلة وكابيشون عميق وأساور وحافة سفلية مضلّعة. عبارة «ابتسامات عامة / نوايا خاصة» بخط عربي أسود على الصدر. جزء من دروب 01 — كمية محدودة.',
  NULL, 'ACTIVE', 'Oversized', 'أوفرسايز', '[FABRIC]', '[الخامة]', '[GSM]', '[GSM]', 'White', 'أبيض',
  '[CARE INSTRUCTIONS]', '[تعليمات العناية]',
  '[ADD MEASUREMENTS IN CM]\nModel is [HEIGHT] and wears size [SIZE].', '[أضف المقاسات بالسنتيمتر]\nطول الموديل [الطول] ويرتدي مقاس [المقاس].',
  (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM products)
FROM drops d WHERE d.number = 1;

INSERT INTO product_variants (product_id, size, sku, stock, position)
SELECT p.id, s.size, CONCAT('HOODIE_WHITE-', s.size), 10, s.pos
FROM products p
CROSS JOIN (SELECT 'S' AS size, 0 AS pos UNION ALL SELECT 'M', 1 UNION ALL SELECT 'L', 2 UNION ALL SELECT 'XL', 3) s
WHERE p.slug = 'hoodie-white';

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id, i.url, i.kind, i.pos, i.alt_en, i.alt_ar
FROM products p
CROSS JOIN (
  SELECT '/assets/products/hoodie-white-front.webp' AS url, 'front' AS kind, 0 AS pos,
         'White hoodie with black Arabic lettering on the chest, held out to show the print' AS alt_en,
         'هودي أبيض بخط عربي أسود على الصدر، مرفوع لإظهار الطبعة' AS alt_ar
  UNION ALL SELECT '/assets/products/hoodie-white-back.webp', 'back', 1,
         'White hoodie worn with a green VOID cap and washed grey jeans',
         'هودي أبيض مع كاب VOID أخضر وجينز رمادي مغسول'
  UNION ALL SELECT '/assets/products/hoodie-white-detail.webp', 'detail', 2,
         'White hoodie laid flat, showing the two lines of black lettering and the ribbed cuffs and hem',
         'هودي أبيض مفرود، يظهر سطرا الكتابة السوداء والأساور والحافة المضلّعة'
  UNION ALL SELECT '/assets/products/hoodie-white-body.webp', 'body', 3,
         'White hoodie worn with wide black trousers and green sneakers, seated',
         'هودي أبيض مع بنطلون أسود واسع وحذاء أخضر، جلوس'
) i
WHERE p.slug = 'hoodie-white';

-- Remove the demo hoodie. If it was ever ordered, keep it but archive it so the order history stays
-- intact; otherwise delete it (its sizes, photos and wishlist hearts go with it).
UPDATE products SET status = 'ARCHIVED'
WHERE slug = 'event-horizon-hoodie'
  AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = products.id);

DELETE FROM products
WHERE slug = 'event-horizon-hoodie'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = products.id);
