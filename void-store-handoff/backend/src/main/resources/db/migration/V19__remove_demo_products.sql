-- Removes the two remaining demo products from the V2 seed (owner request, 2026-11-14): "Singularity
-- Tee" and "Null Sweatpant". The store now sells only the real pieces. If one of them was ever
-- ordered it is archived (hidden from the store) so the order history stays intact; otherwise it is
-- deleted together with its sizes, photos and wishlist hearts.

UPDATE products SET status = 'ARCHIVED'
WHERE slug IN ('singularity-tee', 'null-sweatpant')
  AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = products.id);

DELETE FROM products
WHERE slug IN ('singularity-tee', 'null-sweatpant')
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = products.id);
