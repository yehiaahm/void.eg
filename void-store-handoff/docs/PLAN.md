# VOID — full store build plan

Scope was extended beyond the original phase 1 in CLAUDE.md: a complete, production store with checkout, customer accounts and an admin panel.

## Decisions (from the owner, 2026-10-02)
- Backend: **Spring Boot 3 (Java 21, Maven) + MySQL 8** (MySQL runs in Docker for dev).
- Payments: **Cash on delivery now**, behind a `PaymentProvider` interface so **Paymob** (cards / wallets) plugs in later without touching checkout.
- Accounts: **guest checkout + optional customer account** (order history, saved addresses).
- Admin: products / stock / images · orders · dashboard & reports · drops, coupons, content, staff users with roles.

Defaults chosen (change any time):
- The announcement bar shows on every storefront page.
- The menu search filters products live (name EN/AR, category).
- Sold-out sizes are disabled and struck through; a fully sold-out product shows "Sold out" instead of "Add to bag".
- The bag icon opens a cart drawer; there is a full checkout page.
- Money is stored as integer piastres (EGP × 100); prices display without decimals when whole.

## Repository layout
```
backend/            Spring Boot API (+ Flyway migrations, uploads/)
frontend/           React 18 + Vite + TS — storefront and /admin (admin is a lazy-loaded chunk)
design/, assets/    the original handoff (source of truth for the storefront look)
docs/               this plan, API notes, deploy guide
docker-compose.yml  mysql + mailpit (dev); full stack profile for production
```

## Data model (MySQL)
- `drops` — number, name_en/ar, starts_at, status (upcoming/live/closed)
- `products` — slug, drop_id, category, name_en/ar, description_en/ar, price, compare_at_price, status (draft/active/archived), fit/fabric/weight/colour/care (EN/AR), sort_order
- `product_variants` — product_id, size (S/M/L/XL), sku, stock, `version` (optimistic lock)
- `product_images` — product_id, url, kind (front/back/detail/body), position, alt_en/ar
- `users` — email, password_hash, name, phone, role (CUSTOMER / STAFF / ADMIN / OWNER), enabled
- `addresses` — user_id, name, phone, governorate, city, street, building/floor/apartment, notes
- `shipping_zones` — governorate (all 27), fee, eta_days, enabled
- `coupons` — code, type (percent/fixed/free_shipping), value, min_subtotal, starts/ends, max_uses, per_customer, used_count
- `orders` — number (VOID-000123), user_id (nullable), contact + address snapshot, subtotal, discount, shipping, total, coupon_code, payment_method, payment_status, status (new → confirmed → shipped → delivered | cancelled | returned), notes, timestamps
- `order_items` — snapshot of name, size, sku, unit price, qty, image
- `order_events` — status history + who changed it (audit trail)
- `settings` — announcement text EN/AR, social URLs, store contact, etc.
- `pages` — slug (shipping, returns, privacy, contact), title/body EN/AR
- `refresh_tokens`, `password_reset_tokens`

Stock is reserved inside the order transaction (variant row lock / version check) and restored on cancel or return.

## API (REST, `/api/v1`)
Public: products, product by slug, current/next drop, settings, pages, shipping zones, coupon validation, place order, track order (number + phone).
Customer (JWT): register, login, refresh, logout, forgot/reset password, me, addresses CRUD, my orders.
Admin (JWT + role): dashboard stats, products CRUD + image upload + variant stock, drops, orders (list/filter/detail/status/notes/invoice), customers, coupons, shipping zones, settings, pages, staff users.
Auth: short-lived access token in memory + refresh token in an httpOnly cookie. BCrypt passwords, rate-limited login, role checks on every admin endpoint.

## Storefront pages
Home · Product · Cart drawer · Checkout · Order confirmation · Track order · Account (login / register / orders / addresses / reset password) · Policy pages · Search (menu) · 404. All EN + AR with RTL. New pages use the same tokens and type as the approved design.

## Admin (`/admin`)
Login · Dashboard (revenue, orders, AOV, sales chart, best sellers, low-stock alerts) · Products (list, editor with EN/AR fields, image upload & ordering, stock per size) · Orders (filters, detail, status timeline, printable invoice / shipping label, CSV export) · Customers · Drops · Coupons · Shipping zones · Content (announcement, pages, social links) · Staff & roles.

## Build phases (status 2026-10-02: all done)
0. ✅ Scaffold: git, monorepo, docker-compose (MySQL + Mailpit), Spring Boot skeleton, Vite skeleton.
1. ✅ Storefront UI per the screenshots (home, product, hero canvas, i18n/RTL) — verified against the 1440px / 390px screenshots.
2. ✅ Backend core: Flyway schema, seed data (placeholders kept), public catalog/settings API; frontend on the real API.
3. ✅ Bag drawer, checkout (COD), shipping zones, coupons, row-locked stock reservation, emails, order tracking.
4. ✅ Customer accounts (orders, addresses, profile, password reset).
5. ✅ Admin panel (EN + AR) with STAFF / ADMIN / OWNER roles.
6. ✅ `PaymentProvider` abstraction (COD). Paymob adapter: steps in `docs/DEPLOY.md`, waiting for the owner's account.
7. ✅ Server-side SEO tags, sitemap/robots, JSON-LD; Lighthouse mobile: home 90+, product 94, accessibility 100, SEO 100; Testcontainers integration tests; production Docker + Caddy; deploy guide.

## Next ideas (not started)
- Paymob card / wallet payments.
- Shipping-company integration (Bosta / Mylerz) for automatic waybills and tracking numbers.
- "Notify me" for sold-out sizes and drop launches (the design has no email form — needs the owner's approval).
- Image resizing on upload (currently the owner should upload ~1600px WebP).

## Still needed from the owner
Prices, product photos, size charts, fabric/weight/colour/care/SKU, shipping fees per governorate, policy texts, Instagram/TikTok URLs, drop dates, SMTP account for emails, domain + hosting choice, Paymob account (later).
