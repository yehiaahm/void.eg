-- Initial catalogue from the approved design. Every value the owner still has to provide is
-- NULL (prices, drop dates, shipping fees) or a clearly marked [PLACEHOLDER] string.

INSERT INTO drops (id, number, name_en, name_ar, starts_at, status) VALUES
  (1, 1, 'Singularity',   'التفرّد',   NULL, 'LIVE'),
  (2, 2, 'Event Horizon', 'أفق الحدث', NULL, 'UPCOMING');  -- [PLACEHOLDER] starts_at drives the countdown

INSERT INTO products (id, slug, drop_id, category_en, category_ar, name_en, name_ar, description_en, description_ar,
  price, status, fit_en, fit_ar, fabric_en, fabric_ar, weight_en, weight_ar, colour_en, colour_ar, care_en, care_ar,
  size_fit_en, size_fit_ar, sort_order) VALUES
  (1, 'event-horizon-hoodie', 1, 'Hoodies', 'هوديز', 'Event Horizon Hoodie', 'Event Horizon Hoodie',
   'Cut wide with dropped shoulders, the Event Horizon Hoodie has a deep hood, a kangaroo pocket and ribbed cuffs and hem — nothing extra. Part of Drop 01: once it’s gone, it’s gone.',
   'قصّة واسعة بأكتاف منسدلة، مع كابيشون عميق وجيب أمامي وأساور وحافة سفلية مضلّعة — بلا أي زيادات. جزء من دروب 01: حين ينفد، لا يعود.',
   NULL, 'ACTIVE', 'Oversized', 'أوفرسايز', '[FABRIC]', '[الخامة]', '[GSM]', '[GSM]', '[COLOUR]', '[اللون]',
   '[CARE INSTRUCTIONS]', '[تعليمات العناية]',
   '[ADD MEASUREMENTS IN CM]\nModel is [HEIGHT] and wears size [SIZE].', '[أضف المقاسات بالسنتيمتر]\nطول الموديل [الطول] ويرتدي مقاس [المقاس].', 1),
  (2, 'singularity-tee', 1, 'Tees', 'تيشيرتات', 'Singularity Tee', 'Singularity Tee',
   'A boxy, oversized tee with dropped shoulders, a ribbed crew neck and the VOID mark on the chest. Made to be worn loose. Part of Drop 01 — limited run.',
   'تيشيرت أوفرسايز بقصّة بوكسي وأكتاف منسدلة، ياقة دائرية مضلّعة وشعار VOID على الصدر. مصمَّم ليُلبس واسعًا. جزء من دروب 01 — كمية محدودة.',
   NULL, 'ACTIVE', 'Oversized', 'أوفرسايز', '[FABRIC]', '[الخامة]', '[GSM]', '[GSM]', '[COLOUR]', '[اللون]',
   '[CARE INSTRUCTIONS]', '[تعليمات العناية]',
   '[ADD MEASUREMENTS IN CM]\nModel is [HEIGHT] and wears size [SIZE].', '[أضف المقاسات بالسنتيمتر]\nطول الموديل [الطول] ويرتدي مقاس [المقاس].', 2),
  (3, 'accretion-shell-jacket', 1, 'Jackets', 'جواكت', 'Accretion Shell Jacket', 'Accretion Shell Jacket',
   'A technical shell with a stand collar, a full-length front zip, a zipped chest pocket, hand pockets, adjustable cuffs and a drawcord hem. Part of Drop 01 — limited run.',
   'جاكيت شِل تقني بياقة مرتفعة وسحّاب أمامي كامل، جيب صدر بسحّاب وجيوب جانبية، أساور قابلة للضبط وحافة سفلية برباط. جزء من دروب 01 — كمية محدودة.',
   NULL, 'ACTIVE', '[FIT]', '[القصّة]', '[FABRIC]', '[الخامة]', '[GSM]', '[GSM]', '[COLOUR]', '[اللون]',
   '[CARE INSTRUCTIONS]', '[تعليمات العناية]',
   '[ADD MEASUREMENTS IN CM]\nModel is [HEIGHT] and wears size [SIZE].', '[أضف المقاسات بالسنتيمتر]\nطول الموديل [الطول] ويرتدي مقاس [المقاس].', 3),
  (4, 'null-sweatpant', 1, 'Pants', 'بناطيل', 'Null Sweatpant', 'Null Sweatpant',
   'Relaxed, unisex sweatpants with a drawstring waistband, slant side pockets and ribbed cuffs. Part of Drop 01 — limited run.',
   'بنطلون سويت مريح للجنسين بخصر برباط، جيوب جانبية مائلة وأساور مضلّعة. جزء من دروب 01 — كمية محدودة.',
   NULL, 'ACTIVE', 'Unisex', 'للجنسين', '[FABRIC]', '[الخامة]', '[GSM]', '[GSM]', '[COLOUR]', '[اللون]',
   '[CARE INSTRUCTIONS]', '[تعليمات العناية]',
   '[ADD MEASUREMENTS IN CM]\nModel is [HEIGHT] and wears size [SIZE].', '[أضف المقاسات بالسنتيمتر]\nطول الموديل [الطول] ويرتدي مقاس [المقاس].', 4);

-- [PLACEHOLDER] stock: 10 per size until the owner enters real quantities in the admin.
INSERT INTO product_variants (product_id, size, stock, position)
SELECT p.id, s.size, 10, s.pos
FROM products p
CROSS JOIN (SELECT 'S' AS size, 0 AS pos UNION ALL SELECT 'M', 1 UNION ALL SELECT 'L', 2 UNION ALL SELECT 'XL', 3) s;

-- All 27 governorates. [PLACEHOLDER] fees: zones stay disabled until the owner sets a fee in the admin.
INSERT INTO shipping_zones (code, name_en, name_ar, sort_order) VALUES
  ('cairo', 'Cairo', 'القاهرة', 1),
  ('giza', 'Giza', 'الجيزة', 2),
  ('alexandria', 'Alexandria', 'الإسكندرية', 3),
  ('qalyubia', 'Qalyubia', 'القليوبية', 4),
  ('sharqia', 'Sharqia', 'الشرقية', 5),
  ('dakahlia', 'Dakahlia', 'الدقهلية', 6),
  ('gharbia', 'Gharbia', 'الغربية', 7),
  ('monufia', 'Monufia', 'المنوفية', 8),
  ('beheira', 'Beheira', 'البحيرة', 9),
  ('kafr-el-sheikh', 'Kafr El Sheikh', 'كفر الشيخ', 10),
  ('damietta', 'Damietta', 'دمياط', 11),
  ('port-said', 'Port Said', 'بورسعيد', 12),
  ('ismailia', 'Ismailia', 'الإسماعيلية', 13),
  ('suez', 'Suez', 'السويس', 14),
  ('north-sinai', 'North Sinai', 'شمال سيناء', 15),
  ('south-sinai', 'South Sinai', 'جنوب سيناء', 16),
  ('faiyum', 'Faiyum', 'الفيوم', 17),
  ('beni-suef', 'Beni Suef', 'بني سويف', 18),
  ('minya', 'Minya', 'المنيا', 19),
  ('asyut', 'Asyut', 'أسيوط', 20),
  ('sohag', 'Sohag', 'سوهاج', 21),
  ('qena', 'Qena', 'قنا', 22),
  ('luxor', 'Luxor', 'الأقصر', 23),
  ('aswan', 'Aswan', 'أسوان', 24),
  ('red-sea', 'Red Sea', 'البحر الأحمر', 25),
  ('new-valley', 'New Valley', 'الوادي الجديد', 26),
  ('matrouh', 'Matrouh', 'مطروح', 27);

INSERT INTO settings (k, v) VALUES
  ('shipping_returns_en', '[DELIVERY TIME & SHIPPING COST]\n[PAYMENT OPTIONS]\n[RETURNS & EXCHANGE POLICY]'),
  ('shipping_returns_ar', '[مدة وتكلفة الشحن]\n[طرق الدفع]\n[سياسة الاسترجاع والاستبدال]'),
  ('instagram_url', ''),   -- [PLACEHOLDER]
  ('tiktok_url', ''),      -- [PLACEHOLDER]
  ('contact_email', ''),   -- [PLACEHOLDER]
  ('contact_phone', ''),   -- [PLACEHOLDER]
  ('cod_enabled', 'true'),
  ('low_stock_threshold', '3');

INSERT INTO pages (slug, title_en, title_ar, body_en, body_ar) VALUES
  ('shipping', 'Shipping', 'الشحن', '[SHIPPING POLICY]', '[سياسة الشحن]'),
  ('returns', 'Returns & refunds', 'الاسترجاع والاستبدال', '[RETURNS & REFUNDS POLICY]', '[سياسة الاسترجاع والاستبدال]'),
  ('privacy', 'Privacy policy', 'سياسة الخصوصية', '[PRIVACY POLICY]', '[سياسة الخصوصية]'),
  ('contact', 'Contact', 'تواصل معنا', '[CONTACT DETAILS]', '[بيانات التواصل]');
