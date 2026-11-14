-- VOID TRACE_HOODIE NAVY (owner request, 2026-11-14), Drop 01, 899 EGP (stored as piastres). The chest
-- text reads "الغاية تبرر الوسيلة / THE END JUSTIFIES THE MEANS". It replaces the demo "Accretion Shell
-- Jacket" from the V2 seed (one demo piece goes each time a real product is added).
-- Not invented: fabric / weight / care / size chart stay [PLACEHOLDER] and stock is the usual
-- 10-per-size placeholder. Photos: frontend/public/assets/products/hoodie-navy-*.webp; the cut-out is
-- the flat-lay piece on its own, used on the shop-grid card.

INSERT INTO products (slug, drop_id, category_en, category_ar, name_en, name_ar, description_en, description_ar,
  price, status, fit_en, fit_ar, fabric_en, fabric_ar, weight_en, weight_ar, colour_en, colour_ar, care_en, care_ar,
  size_fit_en, size_fit_ar, sort_order)
SELECT 'void-trace-hoodie-navy', d.id, 'Hoodies', 'هوديز', 'VOID TRACE_HOODIE NAVY', 'VOID TRACE_HOODIE NAVY',
  'An oversized navy hoodie with dropped shoulders, a deep hood, a kangaroo pocket and ribbed cuffs and hem. “The end justifies the means” is set across the chest in yellow, in Arabic and English. Part of Drop 01 — limited run.',
  'هودي أوفرسايز كحلي بأكتاف منسدلة وكابيشون عميق وجيب أمامي وأساور وحافة سفلية مضلّعة. عبارة «الغاية تبرر الوسيلة» بالأصفر على الصدر، بالعربي والإنجليزي. جزء من دروب 01 — كمية محدودة.',
  89900, 'ACTIVE', 'Oversized', 'أوفرسايز', '[FABRIC]', '[الخامة]', '[GSM]', '[GSM]', 'Navy', 'كحلي',
  '[CARE INSTRUCTIONS]', '[تعليمات العناية]',
  '[ADD MEASUREMENTS IN CM]\nModel is [HEIGHT] and wears size [SIZE].', '[أضف المقاسات بالسنتيمتر]\nطول الموديل [الطول] ويرتدي مقاس [المقاس].',
  (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM products)
FROM drops d WHERE d.number = 1;

INSERT INTO product_variants (product_id, size, sku, stock, position)
SELECT p.id, s.size, CONCAT('HOODIE_NAVY-', s.size), 10, s.pos
FROM products p
CROSS JOIN (SELECT 'S' AS size, 0 AS pos UNION ALL SELECT 'M', 1 UNION ALL SELECT 'L', 2 UNION ALL SELECT 'XL', 3) s
WHERE p.slug = 'void-trace-hoodie-navy';

INSERT INTO product_images (product_id, url, kind, position, alt_en, alt_ar)
SELECT p.id, i.url, i.kind, i.pos, i.alt_en, i.alt_ar
FROM products p
CROSS JOIN (
  SELECT '/assets/products/hoodie-navy-front.webp' AS url, 'front' AS kind, 0 AS pos,
         'Navy hoodie with yellow Arabic and English lettering on the chest, worn with a patterned beanie and light jeans' AS alt_en,
         'هودي كحلي بكتابة صفراء عربي وإنجليزي على الصدر، مع بينية منقوشة وجينز فاتح' AS alt_ar
  UNION ALL SELECT '/assets/products/hoodie-navy-back.webp', 'back', 1,
         'Navy hoodie with the hood up, worn with a VOID cap and plaid trousers',
         'هودي كحلي بالكابيشون مرفوع، مع كاب VOID وبنطلون كاروهات'
  UNION ALL SELECT '/assets/products/hoodie-navy-detail.webp', 'detail', 2,
         'Navy hoodie laid flat, showing the yellow chest lettering, the pocket and the ribbed cuffs and hem',
         'هودي كحلي مفرود، يظهر الكتابة الصفراء على الصدر والجيب والأساور والحافة المضلّعة'
  UNION ALL SELECT '/assets/products/hoodie-navy-body.webp', 'body', 3,
         'Navy hoodie worn with wide light-wash jeans, full length, holding a black bag',
         'هودي كحلي مع جينز واسع فاتح، من الرأس للقدم، وفي اليد شنطة سوداء'
  UNION ALL SELECT '/assets/products/hoodie-navy-cutout.webp', 'cutout', 4,
         'Navy hoodie with yellow chest lettering, shown on its own',
         'هودي كحلي بكتابة صفراء على الصدر، القطعة وحدها'
) i
WHERE p.slug = 'void-trace-hoodie-navy';

-- Remove the demo jacket. If it was ever ordered, keep it but archive it so the order history stays
-- intact; otherwise delete it (its sizes, photos and wishlist hearts go with it).
UPDATE products SET status = 'ARCHIVED'
WHERE slug = 'accretion-shell-jacket'
  AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = products.id);

DELETE FROM products
WHERE slug = 'accretion-shell-jacket'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = products.id);
