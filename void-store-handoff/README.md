# VOID — store

Egyptian streetwear store in limited drops: bilingual storefront (EN / AR with RTL), checkout with cash on delivery, customer accounts, and an admin panel.

| | |
|---|---|
| Storefront | React 18 + Vite + TypeScript, React Router, TanStack Query — `frontend/` |
| Admin | Same app at `/admin` (lazy-loaded), EN + AR |
| API | Spring Boot 4 (Java 21), Spring Security (JWT), JPA + Flyway — `backend/` |
| Database | MySQL 8.4 |
| Production | One Docker image (store + API) behind Caddy (automatic HTTPS) |

Design source of truth: `design/` and `CLAUDE.md`. Build plan and decisions: `docs/PLAN.md`. Deployment: `docs/DEPLOY.md`.

## Run locally

**Windows, one click:** double-click `run-all.bat` (starts Docker, the database, the API and the store, creates `backend/local.properties` on first run, then opens the store and the admin). `stop-all.bat` stops everything; your data is kept.

Manual steps:

Prerequisites: Docker Desktop, Java 21, Node 20+.

```bash
docker compose up -d                         # MySQL on :3307, Mailpit on :8025
```

Create `backend/local.properties` (git-ignored) with your local owner account and a JWT secret:

```properties
void.bootstrap.owner-email=owner@void.local
void.bootstrap.owner-password=choose-a-local-password
void.jwt.secret=any-random-string-of-at-least-32-characters
```

```bash
cd backend && ./mvnw spring-boot:run          # API on :8080 (Flyway creates + seeds the DB)
cd frontend && npm install && npm run dev     # store on http://localhost:5173 (proxies /api)
```

- Store: http://localhost:5173 · Admin: http://localhost:5173/admin (sign in with the owner account above)
- All outgoing email (order confirmations, password resets) lands in Mailpit: http://localhost:8025

## Tests

```bash
cd backend && ./mvnw test        # integration tests on a throwaway MySQL (Testcontainers; needs Docker)
cd frontend && npx tsc -b        # type check
```

The tests cover checkout totals, stock locking under concurrent orders (no overselling), coupons, cancellation restocking, order tracking, roles, refresh-token rotation and the closed-store waitlist.

## How the store works

- **Money** is stored as integer piastres (EGP × 100). The admin types pounds.
- **Stock** is reserved when an order is placed (rows are locked, so two shoppers can't buy the last piece) and put back when an order is cancelled or returned.
- **Order flow:** New → Confirmed → Shipped → Delivered (or Cancelled / Returned). Cash-on-delivery orders become *Paid* on delivery. Customers get an email at each step.
- **Shipping:** one fee per governorate (all 27 are listed). Shoppers only see governorates that are enabled and have a fee.
- **Cancelling:** customers (signed in, or guests via Track order with number + phone) can cancel until the order is confirmed.
- **Returns & exchanges:** after delivery, within the return window (Admin → Content, 7 days per the owner's policy), customers pick pieces, a reason, and a new size for exchanges. A refund is only accepted when the reason is our mistake (wrong piece, faulty, not as described); anything else, like a size change, has to be an exchange, and the form says that exchanges not caused by us include shipping fees. Staff review in Admin → Returns: *Approve* (an exchange reserves the new size), *Mark received* (returned pieces go back to stock), *Complete* (refund amount recorded). Stock is never counted twice.
- **Closing the store:** Admin → Dashboard → *Close the store* (Admin / Owner), with an optional message. Shoppers then see a “we’ll be back” page where they leave their name and mobile (email and notes optional); the form is swallowed by the black hole and a check mark confirms it. Orders are refused while closed (staff can still browse and test), while order tracking, accounts and policy pages keep working. Sign-ups land in Admin → Waitlist (mark contacted, WhatsApp link, CSV export).
- **Wishlist:** hearts on every product. Guests keep it on the device; it moves into the account at sign-in. Admin → Products shows how many people saved each piece.
- **Payments:** cash on delivery. Online payment (Paymob) plugs into `backend/.../payment/PaymentProvider` without touching checkout.
- **Roles:** Staff (orders, customers, stock) · Admin (everything but the team) · Owner (everything, including the team).
- **Placeholders:** prices are empty, and texts like `[FABRIC]` are shown as-is until the owner fills them in the admin — they are never invented.

## Before launch — the owner fills these in the admin

| Where | What |
|---|---|
| Products | Prices, photos (front / back / detail / on body), real stock, SKUs, fabric, weight, colour, care, size chart |
| Drops | Drop 02 launch date (drives the countdown — the announcement bar stays hidden until it's set) |
| Shipping | Fee + delivery time for each governorate you deliver to, then enable it |
| Content | Return / exchange window (7 days, from `V7` with the Returns & refunds page text — keep the two in sync if you change it), store email (gets new-order alerts) & phone, shipping-and-returns text, the policy pages (Privacy has a default from `V8` describing what the site collects today — review it, and update it if you add online payment, analytics or a newsletter) |
| Staff | Team members and their roles |
