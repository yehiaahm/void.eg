-- Social links provided by the owner (2026-10-02). Only fills them in if they were still empty,
-- so links changed later in Admin → Content are never overwritten.
UPDATE settings SET v = 'https://www.instagram.com/voiddeg/' WHERE k = 'instagram_url' AND v = '';
UPDATE settings SET v = 'https://www.tiktok.com/@voidd.eg' WHERE k = 'tiktok_url' AND v = '';
