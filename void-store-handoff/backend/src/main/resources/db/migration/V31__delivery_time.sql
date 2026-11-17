-- Delivery time from the owner (2026-11-17): orders arrive within 3 to 5 days. Fills the two
-- placeholders still left in the product-page "Shipping & returns" accordion and states the
-- time on the Shipping page. Each update only replaces the seed placeholder / the generic
-- sentence from V29, so anything already edited in Admin -> Content is kept. The shipping fee
-- is NOT stated: it differs per governorate and is shown at checkout.

UPDATE settings SET v = REPLACE(v, '[DELIVERY TIME & SHIPPING COST]',
  'Delivery in 3 to 5 days. Shipping cost depends on your governorate and is shown at checkout.')
WHERE k = 'shipping_returns_en';
UPDATE settings SET v = REPLACE(v, '[PAYMENT OPTIONS]',
  'Pay cash on delivery.')
WHERE k = 'shipping_returns_en';

UPDATE settings SET v = REPLACE(v, '[مدة وتكلفة الشحن]',
  'التوصيل خلال 3 لـ 5 أيام. تكلفة الشحن بتختلف حسب المحافظة وبتظهر لك قبل تأكيد الطلب.')
WHERE k = 'shipping_returns_ar';
UPDATE settings SET v = REPLACE(v, '[طرق الدفع]',
  'الدفع كاش عند الاستلام.')
WHERE k = 'shipping_returns_ar';

-- Shipping page (V29 text).
UPDATE pages SET body_en = REPLACE(body_en,
  'Shipping cost and delivery time depend on your governorate. Choose your governorate at checkout and you’ll see both before you place your order',
  'Your order arrives within 3 to 5 days. Shipping cost depends on your governorate: choose it at checkout and you’ll see the cost before you place your order')
WHERE slug = 'shipping';

UPDATE pages SET body_ar = REPLACE(body_ar,
  'تكلفة الشحن ومدة التوصيل بيختلفوا حسب المحافظة. اختار محافظتك وإنت بتكمّل الطلب وهتشوف الاتنين قبل ما تأكّد',
  'طلبك بيوصلك خلال 3 لـ 5 أيام. تكلفة الشحن بتختلف حسب المحافظة: اختار محافظتك وإنت بتكمّل الطلب وهتشوف التكلفة قبل ما تأكّد')
WHERE slug = 'shipping';
