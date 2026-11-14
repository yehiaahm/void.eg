-- The owner's name for this piece (2026-11-14) is LONGSLEEVE_BLACK (replaces the working title
-- "Vision Sweatshirt"). Renames the product, its URL slug and the photo descriptions.

UPDATE products
SET slug = 'longsleeve-black', name_en = 'LONGSLEEVE_BLACK', name_ar = 'LONGSLEEVE_BLACK'
WHERE slug = 'vision-sweatshirt';

UPDATE product_images i
JOIN products p ON p.id = i.product_id
SET i.alt_en = CASE i.kind
      WHEN 'front'  THEN 'Black longsleeve worn with light-wash jeans, front view'
      WHEN 'back'   THEN 'Black longsleeve styled with a patterned beanie and clear-lens glasses'
      WHEN 'detail' THEN 'Black longsleeve laid flat, showing the white chest embroidery and raw-cut hem'
      WHEN 'body'   THEN 'Black longsleeve worn with a VOID cap and plaid trousers'
      ELSE i.alt_en END,
    i.alt_ar = CASE i.kind
      WHEN 'front'  THEN 'لونج سليف أسود مع جينز فاتح، من الأمام'
      WHEN 'back'   THEN 'لونج سليف أسود مع بينية منقوشة ونظارة شفافة'
      WHEN 'detail' THEN 'لونج سليف أسود مفرود، يظهر التطريز الأبيض على الصدر والحافة الخام'
      WHEN 'body'   THEN 'لونج سليف أسود مع كاب VOID وبنطلون كاروهات'
      ELSE i.alt_ar END
WHERE p.slug = 'longsleeve-black';
