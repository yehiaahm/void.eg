# VOID — minimal storefront

VOID is an Egyptian streetwear brand (oversized tees & hoodies, technical jackets, unisex basics) that sells in **limited drops**. The logo is a black hole with a chromatic accretion disk. This repo turns the approved **minimal store design** into a production website.

## Source of truth for the design

- `design/screens/*.jpg` — approved screenshots (desktop 1440px + phone 390px, EN + AR, home / menu / product page). Match these.
- `design/store-en.reference.html`, `design/store-ar.reference.html` — the full design prototype: markup, all CSS in the `<style>` block, all behaviour in the `class Component` script at the bottom. They depend on a private canvas runtime (`{{holes}}`, `<sc-if>`, `/_blob/...` image URLs), so they will **not** open in a browser — read them as source and copy the CSS values, keyframes, copy text (EN + AR) and JS logic from them.
- `assets/` — production images (move to `public/assets/`):
  - `void-hole.webp` — hero black hole, transparent background with an opaque event horizon (1480×560; the ring's centre is the image centre).
  - `void-glow-a.webp`, `void-glow-b.webp` — blurred glow layers behind the hole (positioned at left −8.11% / top −21.43%, size 116.22% × 142.86% of the hole box), cross-faded.
  - `icons/instagram.svg`, `icons/tiktok.svg` — footer social icons (`fill="currentColor"`).
  - `void-logo-original.jpg` — original logo, for favicon / social preview only. **It is not shown in the header.**

Blob URL → file mapping in the reference HTML: `/_blob/430dea8e…` = `void-hole.webp`, `/_blob/a09a575a…` = `void-glow-a.webp`, `/_blob/f801d874…` = `void-glow-b.webp`.

When the reference HTML and the screenshots disagree, the screenshots win.

## Stack

- React 18 + Vite + TypeScript, React Router.
- Styling: plain CSS / CSS Modules with the tokens below (no UI kit).
- i18n: English + Arabic with full RTL (`<html dir="rtl" lang="ar">`); the language switch keeps the current route (`/en/...`, `/ar/...`).
- Backend (phase 2): Spring Boot + MySQL REST API (products, drops, cart, orders). Keep the frontend behind a small API layer (`src/api/`) with a mock implementation first.

## Design tokens

- Colours: background `#000000`, surface (image tiles) `#0B0B0E`, text `#F2F0EB`, muted `#A7A49D`, label `#9C9992`, faint `#8A877F`, hairline `rgba(242,240,235,.1)`, warning `#FF9B6A`. Announcement bar and primary button are inverted: `#F2F0EB` background, `#050506` text.
- Fonts (Google Fonts): **Archivo** variable (body 100% width; wordmark and product title at `font-stretch:125%`, weight 800), **JetBrains Mono** (labels, prices, countdown — uppercase, letter-spacing .12em, 11px), **IBM Plex Sans Arabic** (all Arabic text: letter-spacing 0, no uppercase, labels 13px).
- Page padding: `clamp(16px, 3.2vw, 48px)`.
- One breakpoint: phone/tablet ≤ 900px, desktop above. Hover effects only inside `@media (hover:hover) and (pointer:fine)` (on touch they cause double-taps). `prefers-reduced-motion` disables all animation and the particle flow.

## Pages & sections

### Home
1. **Announcement bar** (36px, light) — "Drop 02 — Event Horizon lands in" + live countdown `DD:HH:MM:SS` in mono (always LTR).
2. **Sticky header** (64px desktop / 56px phone, black 90% + blur, hairline bottom) — three columns: left = "Shop" link (desktop) or hamburger (phone); centre = **the word "VOID" only** (Archivo 800, 17px, 125% width, letter-spacing .34em — no logo image); right = `EN / ع` language switch + bag icon with count badge.
   - Phone menu: full-screen black overlay under the header with a **search field** and the nav links. The header (and its close button) stays above it.
3. **Hero** — centred column: animated black hole, kicker "Drop 01 — Singularity", outlined button "Shop the drop" (scrolls to the grid).
   - Hole box: `width:min(76cqw, 920px)`, aspect 1480/560; phone `min(135cqw, 920px)` so it is cropped at both sides. Hero min-height `clamp(560px, 100svh − 100px, 920px)`; phone `clamp(480px, 64svh, 600px)`.
   - Intro: hole opens from a thin flash line (`sHoleIn`, 1.9s), glows fade in and cross-fade forever (8s), slow "breathe" scale (10s).
   - **Canvas particle system** over the whole hero (port `sizeCanvas / measure / seed / spawn / loop / draw` from the reference script): twinkling stars + ~420 particles (half on phone) spiralling into the hole on the disk's tilted plane (13.5°, flattening 0.24), drawn as short additive streaks, warm on one side / cool on the other; particles behind the hole are culled inside the event-horizon circle. The hole radius is measured from an invisible "core" element (27.52% of the hole box width, centred). Pause drawing when the hero is off-screen, the tab is hidden or the product page is open.
4. **Shop all** — header row ("Shop all" + "4 products"), grid of **4 columns desktop / 2 columns phone**. Card = 4:5 image tile, name, price. Nothing else on the card (no sizes, no add button). Desktop hover swaps front → back image. Whole card opens the product page.
5. **Footer** (hairline top) —
   - Desktop: three columns — left: text links (Shipping, Returns & refunds, Privacy policy, Contact); **centre: "© 2026 VOID"**; **right: Instagram + TikTok icons** (26px, 48px tap target, muted → white on hover).
   - Phone: everything centred and stacked — "© 2026 VOID", then links, then the icons (28px).
   - There is **no About section and no email/notify form**.

### Product page (`/:lang/product/:slug`)
- "Back" link to the shop (restore scroll position).
- Gallery of 4 images (front, back, detail, on body): desktop 2×2 grid; phone full-width swipe with scroll-snap + dots.
- Sticky info column (desktop): name, price (EGP), limited-run note, size selector S M L XL, quantity stepper (1–10), "Add to bag" (shows a warning line + toast if no size is selected), description.
- Accordions: Details (fit, fabric, weight, colour, care, SKU), Size & fit, Shipping & returns.
- "More from Drop 01" — the other three products (3 columns desktop / 2 phone).
- Phone: sticky bottom bar with name, price and "Add to bag"; the in-column button is hidden.

### Global
- Bag count shared across pages (context/store, persisted to localStorage). Toast on add: "Added to bag — Name · Size × Qty" (bottom centre, 2.6s; sits above the sticky bar on the phone product page).
- RTL: numbers, prices, countdown and "© 2026 VOID" stay `dir="ltr"`; the back arrow flips; gallery scrolling works in both directions.

## Data (mock first)
Products: `event-horizon-hoodie`, `singularity-tee`, `accretion-shell-jacket`, `null-sweatpant` — name, category, price, sizes + stock, images, description, details. Drop: number, name, `startsAt` (drives the countdown).

## Placeholders the owner still has to provide
Prices, product photos (the line drawings in the design are stand-ins), size-chart measurements, fabric/weight/colour/care/SKU, shipping & returns text, policy pages, Instagram/TikTok URLs, drop date. Keep them as clearly marked `[PLACEHOLDER]` values in the mock data — never invent them.

## Scope update (2026-10-02)
The owner extended the scope to a full store: cart, checkout (cash on delivery, Paymob-ready), optional customer accounts and an admin panel, with a Spring Boot + MySQL backend. See `docs/PLAN.md` for decisions, data model, API and build phases — it overrides the "phase 2" notes above. `README.md` has run/test commands, `docs/DEPLOY.md` the production setup.

### Current layout
- `frontend/` — storefront (`src/pages`, `src/components`), admin (`src/admin`, lazy at `/admin`, own EN/AR strings in `src/admin/i18n.tsx`), API client (`src/api`), store copy (`src/i18n/strings.ts`). The mock API was removed; the dev server proxies `/api` to `:8080`.
- `backend/` — `com.voidstore.api.{catalog,order,payment,shipping,content,user,auth,admin,web,mail,config,common}`. Schema + seed in Flyway (`db/migration`); add new `V3__…` files, never edit applied ones.
- Local secrets live in the git-ignored `backend/local.properties` (owner account + JWT secret).

### Intentional deviations from the screenshots
- Header (owner request, 2026-10-02): desktop left nav = Shop · Track order; right = language · wishlist heart (count) · account · bag. On phones the heart moves into the menu.
- Product cards show the name only — no price (owner request, 2026-10-02). The price appears on the product page, the phone sticky bar, the bag and checkout. Same for the phone-menu search results.
- The announcement bar is hidden until Drop 02 has a `startsAt` date (no fake countdown in production).
- `sHoleIn` starts at opacity 1 (the squashed flash line is visible from frame one) so the hero image counts for LCP; the `[PRODUCT PHOTO]` label uses `--faint` instead of `#6F6C66` for WCAG contrast.
- Pages beyond the design (bag drawer, checkout, account, tracking, policy pages, admin) reuse the same tokens and type.

### Returns / wishlist (V3)
- `returns` package: REQUESTED → APPROVED → RECEIVED → COMPLETED (or REJECTED / CANCELLED). EXCHANGE reserves the new size at APPROVED; returned pieces are restocked at RECEIVED via `order_items.returned_qty`, which `OrderService.restock` also respects.
- Wishlist: `/api/v1/me/wishlist` for accounts, `localStorage void.wishlist.v1` for guests, merged on sign-in (`store/wishlist.tsx`).
- "Add to bag" animation: `lib/flyToBag.ts` (has a timeout fallback because animations pause in background tabs).
- Owner's returns policy (V7, 2026-10-07): exchanges within 7 days of delivery; a RETURN (refund) is refused with `refund_not_allowed` unless `Reason.ourMistake()` (WRONG_ITEM / DEFECT / NOT_AS_DESCRIBED). The return form only offers those reasons for a refund and shows the shipping-fee hint. V7 also fills the Returns & refunds page text and the returns line of the product-page "Shipping & returns" accordion.
- Policy pages (`InfoPage`) are plain text from the admin with light formatting: blank line = new paragraph, `## ` = heading, `- ` = bullet point.

### Closed store / waitlist (V5)
- Setting `store_closed` (+ optional `closed_message_en/ar`), toggled from Admin → Dashboard (`admin/StoreSwitch.tsx`, `PUT /api/v1/admin/store-status`, ADMIN+). While closed, `StoreLayout` in `App.tsx` renders `pages/ClosedPage.tsx` for every route except track / account / order / pages, signed-in staff see the shop with a warning bar, and `POST /orders` answers 503 `store_closed` for non-staff.
- No flash of the shop: `SpaController` adds `<html data-closed="1">` in production; the client also remembers the last answer in `localStorage void.closed`.
- Sign-ups: `waitlist` package, `POST /api/v1/waitlist` (rate-limited; same mobile updates the open entry), Admin → Waitlist. The black hole is `components/VoidStage.tsx` (shared with the order confirmation); `BlackHole.pulse()` makes the particles surge when the form is pulled in.

### Contact page (V6)
- `/:lang/pages/contact` is `pages/ContactPage.tsx` (not the admin-edited InfoPage): intro copy + the `contact_email` setting as a mailto link, then a form (name, email, optional phone, message) styled after the owner's reference (underlined fields, boxed message, outlined Send).
- `contact` package: `POST /api/v1/contact` (rate-limited) stores the message and emails it to `contact_email` if set; Admin → Messages (`/api/v1/admin/messages`, unread count in the sidebar).

### Conventions
- Money = integer piastres everywhere in the API; `formatPrice()` (store) / `egp()` (admin) to display.
- Never invent owner data: leave `null` / `[PLACEHOLDER]` and list it in the README's "Before launch" table.
- Shell heredocs break on apostrophes in this environment — write multi-line patch scripts with the Write tool instead.

## Definition of done (phase 1)
- Home + product page match the screenshots at 390px and 1440px in EN and AR.
- No horizontal scroll at any width; hero animation runs smoothly on a mid-range phone; Lighthouse mobile performance ≥ 85; every interactive element reachable by keyboard with visible focus.
