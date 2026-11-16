-- Shipping policy page (2026-11-16). Written only from what the store really does today: one fee
-- and one delivery time per governorate (shown at checkout, set in Admin -> Shipping), cash on
-- delivery, e-mails at each order step, order tracking, exchange shipping fees from V7. It states
-- NO fee, NO delivery time and NO shipping company: those are the owner's to fill in (Admin ->
-- Shipping / Content). Only replaces the seed placeholder, so a policy already edited in Admin ->
-- Content is kept. Format: "## " heading, "- " bullet (InfoPage.tsx).

UPDATE pages SET body_en = CONCAT(
  'We deliver across Egypt. This page explains how shipping works at VOID. If you have any questions, send us a message from our Contact page.\n\n',
  '## Where we deliver\n',
  'You can order to any governorate listed at checkout. If yours isn’t there, we don’t deliver to it yet.\n\n',
  '## Shipping cost and delivery time\n',
  'Shipping cost and delivery time depend on your governorate. Choose your governorate at checkout and you’ll see both before you place your order — what you see there is what you pay. Some orders may qualify for free shipping with a promo code.\n\n',
  '## Payment\n',
  'Orders are currently paid cash on delivery. You pay the courier when your order arrives, so please keep the total shown at checkout ready.\n\n',
  '## From order to your door\n',
  '- Order placed: we email you a confirmation with your order number.\n',
  '- Confirmed: our team checks your order and gets it ready.\n',
  '- Shipped: your order is handed to the delivery company and we email you.\n',
  '- Delivered: you receive it and pay the courier. We email you again.\n\n',
  'Because every piece is made in limited quantities, we reserve your size as soon as you order.\n\n',
  '## Tracking your order\n',
  '- With an account: open the order from your Account page.\n',
  '- As a guest: use the link in your confirmation email, or go to Track order and enter your order number and mobile number.\n\n',
  '## Before you order\n',
  'Please double-check your name, mobile number and address at checkout. The delivery company will call the number you give us, so make sure it’s reachable. We can’t deliver an order we can’t reach you about.\n\n',
  '## Exchanges and returns\n',
  'If you need to exchange something, shipping fees apply unless the issue is our mistake (wrong piece, defect or damage), in which case we cover them. The full details are on our Returns & refunds page.'
) WHERE slug = 'shipping' AND body_en = '[SHIPPING POLICY]';

UPDATE pages SET body_ar = CONCAT(
  'بنوصّل لكل أنحاء مصر. الصفحة دي بتشرح الشحن بيتم إزاي مع VOID. لو عندك أي سؤال، ابعتلنا رسالة من صفحة «تواصل معنا».\n\n',
  '## بنوصّل فين\n',
  'تقدر تطلب لأي محافظة موجودة في صفحة إتمام الطلب. ولو محافظتك مش موجودة، يبقى لسه مش بنوصّل لها.\n\n',
  '## تكلفة الشحن ومدة التوصيل\n',
  'تكلفة الشحن ومدة التوصيل بيختلفوا حسب المحافظة. اختار محافظتك وإنت بتكمّل الطلب وهتشوف الاتنين قبل ما تأكّد — اللي بتشوفه هناك هو اللي بتدفعه. وممكن بعض الطلبات تتشحن مجانًا بكود خصم.\n\n',
  '## الدفع\n',
  'الدفع حاليًا كاش عند الاستلام. بتدفع للمندوب لما طلبك يوصل، فياريت تجهّز الإجمالي اللي ظاهر قدامك وإنت بتكمّل الطلب.\n\n',
  '## من الطلب لحد باب بيتك\n',
  '- تم الطلب: هنبعتلك إيميل تأكيد برقم الطلب.\n',
  '- تم التأكيد: فريقنا بيراجع طلبك ويجهّزه.\n',
  '- تم الشحن: طلبك اتسلّم لشركة الشحن وهنبعتلك إيميل.\n',
  '- تم التسليم: طلبك وصلك وبتدفع للمندوب. وهنبعتلك إيميل تاني.\n\n',
  'عشان كل قطعة بتتعمل بكميات محدودة، بنحجزلك المقاس بتاعك أول ما تطلب.\n\n',
  '## تتبّع طلبك\n',
  '- لو عندك حساب: افتح الطلب من صفحة «الحساب».\n',
  '- لو طلبت من غير حساب: استخدم اللينك اللي في إيميل التأكيد، أو ادخل على «تتبّع الطلب» واكتب رقم الطلب ورقم الموبايل.\n\n',
  '## قبل ما تطلب\n',
  'راجع اسمك ورقم موبايلك وعنوانك كويس وإنت بتكمّل الطلب. شركة الشحن هتتصل بالرقم اللي تديهولنا، فاتأكد إنه شغال. مش هنقدر نوصّل طلب مقدرناش نوصلك بخصوصه.\n\n',
  '## الاستبدال والاسترجاع\n',
  'لو محتاج تستبدل حاجة، مصاريف الشحن عليك إلا لو الغلط مننا (قطعة غلط، عيب، أو تلف)، ساعتها إحنا اللي بنتحمّلها. التفاصيل كاملة في صفحة «الاسترجاع والاستبدال».'
) WHERE slug = 'shipping' AND body_ar = '[سياسة الشحن]';
