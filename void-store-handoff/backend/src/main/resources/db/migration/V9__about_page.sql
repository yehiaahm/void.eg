-- About (owner copy, 2026-10-08): shown as the brand-story section under the shop grid on the
-- home page (components/About.tsx) and at /pages/about. Edited in Admin → Content like the policy
-- pages; blank line = new paragraph. The Arabic is the owner's text, the English a translation of
-- it. The "VOID — Nothing is truly empty." sign-off is part of the section, not of this text.

INSERT INTO pages (slug, title_en, title_ar, body_en, body_ar) VALUES ('about', 'About', 'عن VOID',
  CONCAT(
    'VOID was born to break the ordinary, so that clothes would never be just fabric you put on.\n',
    'In our world, every piece is an idea, a state of mind, or a moment that won’t come again.\n\n',
    'Every piece has its own orbit.\n',
    'Some carry a gravity you can’t resist, some a motion that never settles, and some the chaos that comes before every new form.\n\n',
    'We don’t move in one direction, and we don’t make clothes that look like everyone else’s.\n',
    'We draw our inspiration from the void and from contradiction, and turn them into pieces that look like nothing but themselves.\n\n',
    'Every drop is a new chapter, and every piece a line in the story.'
  ),
  CONCAT(
    'اتولدت VOID عشان تكسر المألوف، وعشان اللبس ما يفضلش مجرد قماش بيتلبس.\n',
    'في عالمنا، كل قطعة فكرة، أو حالة، أو لحظة مش هتتكرر.\n\n',
    'كل قطعة وليها مدارها.\n',
    'منها جاذبية ما بتتقاومش، ومنها حركة ما بتهداش، ومنها فوضى بتسبق كل شكل جديد.\n\n',
    'ما بنمشيش في اتجاه واحد، وما بنعملش لبس شبه الكل.\n',
    'بناخد إلهامنا من الفراغ ومن التناقض، ونطلّع منهم قطع ما تشبهش غير نفسها.\n\n',
    'كل Drop فصل جديد، وكل قطعة سطر في الحكاية.'
  ));
