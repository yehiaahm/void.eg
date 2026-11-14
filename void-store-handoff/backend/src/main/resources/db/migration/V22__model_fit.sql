-- "Worn by" model info on the product page (owner, 2026-11-14). Each product lists the models who
-- wore it in the photos: first name, height in cm and the size they are wearing. The storefront shows
-- it inside "Size & fit" and, under the size buttons, who wears the size the shopper picked.
--
--   Abdullah  178 cm  hoodies L   · LONGSLEEVE_BLACK XL
--   Ziad      176 cm  hoodies M   · LONGSLEEVE_BLACK L
--   Youssef   193 cm  hoodies L   · LONGSLEEVE_BLACK XL
--
-- Only these three facts were provided. Chest / length / sleeve measurements in cm are still NOT
-- known and are not invented: the old "[ADD MEASUREMENTS IN CM]" placeholder text is replaced with a
-- short fit note that does not state any measurement.

CREATE TABLE product_fit_models (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id  BIGINT       NOT NULL,
  name_en     VARCHAR(80)  NOT NULL,
  name_ar     VARCHAR(80)  NOT NULL,
  height_cm   INT          NOT NULL,
  size        VARCHAR(8)   NOT NULL,
  position    INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_fit_models_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT ck_fit_models_height CHECK (height_cm BETWEEN 100 AND 250),
  INDEX ix_fit_models_product (product_id, position)
);

-- Models in the order the owner listed them. size per product family:
-- hoodies -> Abdullah L, Ziad M, Youssef L; longsleeve -> Abdullah XL, Ziad L, Youssef XL.
INSERT INTO product_fit_models (product_id, name_en, name_ar, height_cm, size, position)
SELECT p.id, m.name_en, m.name_ar, m.height_cm,
       CASE WHEN p.slug = 'longsleeve-black' THEN m.size_tee ELSE m.size_hoodie END,
       m.pos
FROM products p
CROSS JOIN (
  SELECT 'Abdullah' AS name_en, 'عبدالله' AS name_ar, 178 AS height_cm, 'L' AS size_hoodie, 'XL' AS size_tee, 0 AS pos
  UNION ALL SELECT 'Ziad',   'زياد', 176, 'M', 'L',  1
  UNION ALL SELECT 'Youssef', 'يوسف', 193, 'L', 'XL', 2
) m
WHERE p.slug IN ('void-trace-hoodie-white', 'void-trace-hoodie-navy', 'longsleeve-black');

-- Replace the placeholder text (only where it is still the untouched placeholder, so anything the
-- owner already typed in Admin → Products is left alone).
UPDATE products
SET size_fit_en = 'Cut oversized, with dropped shoulders and a roomy body. Between two sizes? Go by the model closest to your height — the sizes they wear are listed below.',
    size_fit_ar = 'قصّة أوفرسايز بأكتاف منسدلة وجسم واسع. بين مقاسين؟ اختر بحسب الموديل الأقرب لطولك — مقاس كل منهم موضّح أدناه.'
WHERE slug IN ('void-trace-hoodie-white', 'void-trace-hoodie-navy')
  AND size_fit_en LIKE '%[ADD MEASUREMENTS IN CM]%';

UPDATE products
SET size_fit_en = 'Boxy, oversized cut with dropped shoulders and a raw-cut hem. Between two sizes? Go by the model closest to your height — the sizes they wear are listed below.',
    size_fit_ar = 'قصّة بوكسي أوفرسايز بأكتاف منسدلة وحافة سفلية بقصّة خام. بين مقاسين؟ اختر بحسب الموديل الأقرب لطولك — مقاس كل منهم موضّح أدناه.'
WHERE slug = 'longsleeve-black'
  AND size_fit_en LIKE '%[ADD MEASUREMENTS IN CM]%';
