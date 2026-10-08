-- Returns & refunds policy from the owner (2026-10-07): exchanges within 7 days of delivery,
-- refunds only when the mistake is ours. Each update only replaces the seed placeholder, so
-- anything already edited in Admin → Content is kept.

UPDATE settings SET v = '7' WHERE k = 'return_window_days' AND v = '14';

UPDATE pages SET body_en = CONCAT(
  'We want you to love every piece. If something isn’t quite right, we’re here to help.\n\n',
  '## Exchanges\n',
  'You can request an exchange within 7 days of receiving your order.\n',
  '- If you exchange for a reason that isn’t our mistake (a size change, for example), shipping fees apply.\n',
  '- If it is our mistake (wrong piece, defect, damage), we exchange it at no extra shipping cost.\n\n',
  '## Refunds\n',
  'We don’t offer refunds unless the issue is on our side — for example, you received the wrong piece or it arrived defective.\n\n',
  '## How to request an exchange or a refund\n',
  '- With an account: open the order from your Account page and choose “Return or exchange”.\n',
  '- As a guest: go to Track order, enter your order number and mobile number, then choose “Return or exchange”.\n\n',
  'Pick the pieces and the reason, and add a note describing the issue. If a piece is defective or not what you ordered, we may ask you for a photo of it.\n\n',
  'Our team reviews every request and gets back to you with the next steps as soon as possible. Questions? Send us a message from the Contact page.'
) WHERE slug = 'returns' AND body_en = '[RETURNS & REFUNDS POLICY]';

UPDATE pages SET body_ar = CONCAT(
  'عاوزين كل قطعة توصلك تعجبك. ولو في حاجة مش مظبوطة، إحنا موجودين نساعدك.\n\n',
  '## الاستبدال\n',
  'تقدر تطلب استبدال خلال 7 أيام من استلام طلبك.\n',
  '- لو الاستبدال لسبب مش غلطة مننا (زي تغيير المقاس)، مصاريف الشحن عليك.\n',
  '- لو الغلط مننا (قطعة غلط، عيب، أو تلف)، هنستبدلها من غير أي مصاريف شحن إضافية.\n\n',
  '## استرداد المبلغ\n',
  'مش بنرجّع المبلغ إلا لو المشكلة من عندنا — زي إن القطعة اللي وصلتك مش اللي طلبتها أو فيها عيب.\n\n',
  '## إزاي تطلب استبدال أو استرداد\n',
  '- لو عندك حساب: افتح الطلب من صفحة «الحساب» واختار «استرجاع أو استبدال».\n',
  '- لو طلبت من غير حساب: ادخل على «تتبّع الطلب»، اكتب رقم الطلب ورقم الموبايل، وبعدها اختار «استرجاع أو استبدال».\n\n',
  'اختار القطع والسبب، واكتب ملاحظة توضّح المشكلة. ولو القطعة فيها عيب أو مش اللي طلبته، ممكن نطلب منك صورة ليها.\n\n',
  'فريقنا بيراجع كل طلب وهيتواصل معاك بالخطوات الجاية في أسرع وقت. عندك سؤال؟ ابعتلنا رسالة من صفحة «تواصل معنا».'
) WHERE slug = 'returns' AND body_ar = '[سياسة الاسترجاع والاستبدال]';

-- Short version for the "Shipping & returns" accordion on the product page.
UPDATE settings SET v = REPLACE(v, '[RETURNS & EXCHANGE POLICY]',
  'Exchanges within 7 days of delivery. Refunds only if you received the wrong piece or it arrived defective — see Returns & refunds.')
WHERE k = 'shipping_returns_en';
UPDATE settings SET v = REPLACE(v, '[سياسة الاسترجاع والاستبدال]',
  'الاستبدال خلال 7 أيام من الاستلام. استرداد المبلغ بس لو القطعة وصلت غلط أو فيها عيب — التفاصيل في صفحة الاسترجاع والاستبدال.')
WHERE k = 'shipping_returns_ar';
