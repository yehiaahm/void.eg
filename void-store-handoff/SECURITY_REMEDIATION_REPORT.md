# VOID Store — Security Remediation Report

Covers audit findings VOID-01 to VOID-06 and PV-1 to PV-5 from [SECURITY_AUDIT_REPORT.md](SECURITY_AUDIT_REPORT.md).
Date: 2026-11-13. Nothing was committed, pushed or deployed; the tree is not a git repository, so there is no diff to review. Every changed file is listed below.

## 0. Read this first: what was and was not verified

| Layer | Result | Evidence |
|---|---|---|
| Backend unit tests (no Docker needed) | **102 / 102 pass** | `mvn -o test -Dtest='!StoreFlowTests'` → `Tests run: 102, Failures: 0, Errors: 0`, BUILD SUCCESS |
| Backend compile + test-compile | pass | included in the run above |
| Backend production package | pass | `mvn -DskipTests package` → `target/void-api-0.0.1-SNAPSHOT.jar` (needed to download one Maven plugin) |
| **Real start-up of the packaged jar** (VOID-01) | **verified** | booted with no secret, with the former default, with a short secret → each aborts with the policy message *before* any database access; with a strong random secret it gets past the check and fails only on `Communications link failure` (no MySQL here) |
| Frontend `tsc -b`, lint, production build | pass | `tsc` clean; `oxlint` 0 errors (pre-existing warnings only); `npm run build` succeeded |
| `npm audit` | unchanged: 1 high (Vite dev server), 2 moderate (react-router) | not part of this task; see VOID-15 in the audit |
| **Integration tests `StoreFlowTests`** (updated and extended) | **NOT RUN** | Docker is unavailable; running it fails with `Could not find a valid Docker environment`. The file compiles. |
| **Flyway migration `V10`, Hibernate schema validation, the new JPQL queries** | **NOT RUN** | they need MySQL. SQL syntax, the `ddl-auto: validate` match for the new columns and the HQL parse have not been exercised. This is the main residual risk of the change set. |
| **Database row-lock behaviour (PV-1)** | **only mock-verified** | unit tests prove the lock is taken first and that a stale status is re-read; whether MySQL serialises two real transactions as intended is untested (integration test written, not run) |
| Browser / UI behaviour of the changed pages | **NOT RUN** | no browser session; type-checked and built only. The frontend has no test runner, so no frontend unit tests were added. |
| Maven dependency scan (SCA) | not run | only `npm audit` available |

**Before this goes anywhere near production:** run `StoreFlowTests` (needs Docker), start the stack once against a throw-away MySQL so Flyway applies `V10` and Hibernate validates, and click through checkout, tracking via an e-mailed link, cancel, and the admin Content page.

---

## 1. Behaviour changes you should know about

1. **The API will not start without a strong `JWT_SECRET`.** Any existing deployment using a weak value, the old dev default, or a value copied from the examples will refuse to start until the secret is replaced. Replacing it signs everyone out. Generate with `openssl rand -base64 48`.
2. **Guest tracking changed.** Order number + phone now shows only a *masked read-only status*. The full delivery details and the *Cancel / Return* buttons require the private link in the confirmation e-mail (`/track?order=…&t=…`). Links in e-mails sent *before* the update have no token; those customers see the masked view until a new e-mail goes out (any status change sends one).
3. **Coupon error messages are generic.** Every reason a code fails now shows the same “This code isn't valid.”, including “below minimum”.
4. **Per-customer coupon limits** now match on phone, delivery address and signed-in account as well as a canonical e-mail (`+tag` and Gmail dots removed). Two people sharing a phone or an address share one allowance.
5. **New purchase limits:** at most 10 pieces per order; at most 2 unconfirmed (`NEW`) orders per phone and per e-mail (staff test orders exempt).
6. **Unconfirmed orders are auto-cancelled** after 72 hours by default (Admin → Content → “Cancel orders nobody confirmed after (hours)”, 0 = never) and the stock is restocked. If staff routinely take longer than 72 h to confirm, raise it or set 0 **before deploying**.
7. **Login limits:** 40 attempts per address and 10 *failed* attempts per account (any address) per 15 minutes; forgot-password also limited to 3 per mailbox per hour. A determined attacker can therefore lock a victim out of login for up to 15 minutes (the usual trade-off for account lockout).
8. **Proxy trust:** `X-Forwarded-For` is only honoured from trusted proxies (`TRUSTED_PROXIES`, default loopback + private ranges). `server.forward-headers-strategy` is now `none`.
9. New `AUDIT` log lines on stdout (see VOID-06).

---

## 2. Findings

### VOID-01 — JWT default secret

1. **Original severity:** HIGH (conditional on deployment).
2. **Root cause:** `application.yml` shipped a public default secret and `SecurityConfig` only logged a warning for it.
3. **Files modified:** `backend/src/main/resources/application.yml`, `backend/src/main/java/com/voidstore/api/config/SecurityConfig.java`; **new** `config/JwtSecretPolicy.java`, `config/JwtSecretStartupCheck.java`, `src/main/resources/META-INF/spring.factories`; docs/examples: `README.md`, `docs/DEPLOY.md`, `.env.prod.example`, `backend/.env.example`.
4. **Fix:** the default is gone (`secret: ${JWT_SECRET:}`). A startup listener validates the secret as soon as the environment is loaded, before any bean or the database; `jwtKey` repeats the check. Rejected: missing/blank, surrounding whitespace, < 32 bytes, known placeholders (`dev-only`, `change-me`, `generate-…`, `example`, `test-secret`, `password`, `0123456789`, …), fewer than 12 distinct characters or Shannon entropy < 3 bits/char. Messages name the rule and never contain the secret. There is no development default at all (dev uses `local.properties`, which `run-all.bat` still generates with a valid random secret).
5. **Regression tests:** `JwtSecretPolicyTest` (17), `SecurityConfigJwtTest` (5: a token signed with the former default and role `OWNER` is **rejected** by the real decoder; wrong-secret and expired tokens rejected; own tokens accepted), `StartupSecretCheckTest` (4: real `SpringApplication` aborts for missing / former default / short secret, never echoes it, starts with a strong one).
6. **Actual results:** 26 / 26 pass. Packaged jar verified manually as described in section 0.
7. **Remaining limitations:** the check cannot judge a secret that is long and random-looking but leaked; rotate on any suspicion. `COOKIE_SECURE` still defaults to `false` (VOID-13, not in this task).

### VOID-02 — Login rate limiting (with PV-2 and PV-3)

1. **Original severity:** MEDIUM.
2. **Root cause:** one limiter key (`IP:email`) so changing the e-mail gave a fresh budget, no per-account or per-IP total; client IP taken from a header the app trusted from anyone (left-most `X-Forwarded-For`); unbounded in-memory limiter keyed by attacker-sized strings.
3. **Files modified:** `auth/AuthController.java`, `auth/AuthService.java`, `auth/RateLimiter.java`, `contact/ContactController.java`, `waitlist/WaitlistController.java`, `application.yml`; **new** `auth/ClientIpResolver.java`.
4. **Fix:** three independent layers on login — every attempt counts per client address (40 / 15 min, stops spraying and caps bcrypt CPU), failed attempts count per account regardless of address (10 / 15 min, stops distributed attacks), and the check runs before the password is verified. `ClientIpResolver` reads `X-Forwarded-For` only when the TCP peer is in `void.trusted-proxies`, then walks the chain right-to-left skipping trusted hops (a value the client prepended is never used; garbage entries are discarded; no DNS lookups); IPv6 clients are bucketed by /64. The limiter is now bounded (100 000 keys, fails closed), windows capped at 1 h, and only the bucket category is logged. All limiter call sites use the resolver. Forgot-password is limited per mailbox as well.
5. **Regression tests:** `AuthControllerLoginLimitTest` (10: rotating e-mails from one address → blocked at request 41; 10 addresses × 1 try on one account → 11th from a fresh address blocked before any password check; case/whitespace-insensitive account key; spoofed `X-Forwarded-For` from an untrusted peer and a prepended fake behind the proxy never grant a fresh budget; separate real clients keep separate budgets; disabled-account responses not counted; oversized fields rejected; forgot-password per mailbox), `ClientIpResolverTest` (8), `RateLimiterTest` (5, incl. bounded-map fail-closed).
6. **Actual results:** 23 / 23 pass.
7. **Remaining limitations:** limiter is in-memory, single-instance (use Redis if scaled out). Account lockout can be used to annoy a known e-mail for 15 min. No CAPTCHA. Behind a platform proxy you must confirm it overwrites/appends the real client IP and, if other private hosts can reach the app, narrow `TRUSTED_PROXIES`. The behaviour behind real Caddy was not tested.

### VOID-03 — Quote rate limit / coupon enumeration

1. **Original severity:** MEDIUM.
2. **Root cause:** `/orders/quote` had no limiter and returned distinct coupon error reasons.
3. **Files modified:** `order/OrderController.java`, `order/OrderService.java`, `order/OrderDtos.java`.
4. **Fix:** quote limited to 120 / 5 min per address; invalid coupon attempts (quote or checkout) limited to 10 / 15 min per address and refused with 429 *before* the lookup once exceeded; valid codes and coupon-less quotes never consume that budget; every failure reason is now the single generic `invalid` (and `coupon_invalid` at checkout). Request fields length-capped (code 40, e-mail 190, slug 120, size 8, etc.). Frontend quote requests now send e-mail/phone only once they look complete, so typing does not burn the quote budget.
5. **Regression tests:** `OrderControllerTest` (quote limit, guess budget, valid codes not counted, field limits), `OrderServiceTest` (seven different failure states all return `invalid`; a usable coupon still works; bad coupon at checkout reports only `coupon_invalid`).
6. **Actual results:** pass (within the 102).
7. **Remaining limitations:** a customer with a real code below the minimum now sees “isn't valid” (UX cost of not leaking). Quote with a known e-mail + valid code still shows whether *that* e-mail already used it (minor oracle that needs a valid code).

### VOID-04 — Guest tracking / cancel / return authorisation

1. **Original severity:** MEDIUM.
2. **Root cause:** sequential order number + phone (not a secret) authorised full PII access and actions.
3. **Files modified:** `order/OrderController.java`, `order/OrderDtos.java`, `order/OrderService.java`, `order/OrderEmails.java`, `returns/CustomerOrders.java`, `returns/ReturnDtos.java`, `returns/ReturnEmails.java`; **new** `order/TrackingTokens.java`; frontend `pages/TrackPage.tsx`, `pages/OrderPlacedPage.tsx`, `pages/CheckoutPage.tsx`, `api/index.ts`, `api/types.ts`, `lib/errors.ts`, `i18n/strings.ts`.
4. **Fix:** a 256-bit per-order token `HMAC-SHA256(derived key, "order-track:v1:" + number)`, nothing stored, verified in constant time, included in the confirmation/status/return e-mail links and returned once by `POST /orders`. The token is required to cancel, to request a return, and for the full view. Number + phone only returns a masked read-only view (initials, masked phone/e-mail, no address, no actions). Even the token view masks phone and e-mail. Phone guessing is limited to 8 failures per order per hour *on the phone path only*, so nobody can lock the real owner out; 20 lookups per 10 min per address. The track page opens the order directly from the e-mailed link and shows a hint in the limited view.
5. **Regression tests:** `TrackingAndIdentityTest` (tokens deterministic, per order, rejected for other orders/secrets/oversize/the order number; masking for both views), `CustomerOrdersAndLocksTest` (token opens, phone/number do not; cancel needs the token and takes the lock first; concurrent confirmation refuses the cancel), `OrderControllerTest` (phone-only gets the masked view, no proof rejected, wrong token indistinguishable not-found, cancel needs token, token path not blocked by phone guessing, shared address budget). Integration tests in `StoreFlowTests` updated (token cancel/return flow, masked view).
6. **Actual results:** unit tests pass; **integration tests not run**.
7. **Remaining limitations:** order numbers are still sequential (now harmless without the token). Whoever holds the e-mail link has the authority (forwarded e-mail = shared access). Tokens are derived from the JWT secret: rotating it invalidates all existing links. Pre-update e-mails carry no token. UI not clicked through.

### VOID-05 — Per-customer coupon identity

1. **Original severity:** MEDIUM.
2. **Root cause:** limit keyed on a typed, unverified e-mail.
3. **Files modified:** `order/OrderService.java`, `order/CouponRedemption.java`, `order/CouponRedemptionRepository.java`; **new** `order/CustomerIdentity.java`, `db/migration/V10__security_hardening.sql`.
4. **Fix:** redemptions now store canonical e-mail, phone, account id and a hash of the normalised delivery address; the limit counts a prior redemption matching **any** of them (existing rows are backfilled with phone and account from their orders). `canonicalEmail` strips `+tag` and Gmail dots/`googlemail`.
5. **Regression tests:** `TrackingAndIdentityTest` (canonicalisation, address key ignores case/punctuation, distinguishes real addresses incl. Arabic), `OrderServiceTest` (the repository is queried with canonical e-mail, phone, address key and user id; a new e-mail with the same phone is refused; no identifiers → no query). Integration: new e-mail + same phone refused (`StoreFlowTests`, not run).
6. **Actual results:** unit tests pass; integration not run; **the JPQL query itself has not been parsed by Hibernate**.
7. **Remaining limitations:** someone with a new e-mail, new phone *and* a differently spelled address still gets a new allowance; only verified identities (OTP, accounts, online payment) close that. Households sharing a phone/address share a limit.

### VOID-06 — Security audit logging

1. **Original severity:** MEDIUM.
2. **Root cause:** no security events were logged.
3. **Files modified:** `auth/AuthService.java`, `auth/RateLimiter.java`, `admin/AdminStoreController.java`, `admin/AdminOrderController.java`, `admin/AdminCatalogController.java`, `waitlist/AdminWaitlistController.java`, `contact/AdminContactController.java`, `returns/AdminReturnController.java`, `common/GlobalExceptionHandler.java`, `order/OrderController.java`, `returns/CustomerOrders.java`; **new** `auth/AuditLog.java`.
4. **Fix:** an `AUDIT` logger writes one line per event: `event=… actor=<id|anonymous> role=… ip=… method=… path=… key="value"`. Events: login success / failed / disabled, register, refresh unknown / **reuse** / rejected, logout, password reset requested / completed / rejected, password change / failed, `rate_limit.exceeded` (bucket category only), `authz.denied`, staff created / updated (WARN), customer enabled/disabled, coupon create/update/delete, shipping, settings (key names only), pages, store open/close, product create/update/delete, drop delete, order status / address edit / guest cancel, return status with refund amount, waitlist/message deletes, tracking failures, auto-cancels, and every **export** (`export.orders`, `export.waitlist`; ≥ 200 rows → WARN `*.large`). E-mails are logged as a 12-hex SHA-256 fingerprint, never plain; passwords/tokens are never passed; all values have control characters stripped, quotes escaped, length capped so input cannot forge log lines.
5. **Regression tests:** `AuditLogTest` (7: structured line and levels, log-forging attempts neutralised, truncation, fingerprint hides the address, request context uses the resolved IP and ignores a spoofed header, authenticated actor/role, failed login logged without password or plain e-mail).
6. **Actual results:** 7 / 7 pass.
7. **Remaining limitations:** logs go to stdout only; shipping, retention and alert rules are yours to set up. Invalid/expired bearer tokens rejected by the filter chain (401) are not logged. Not every admin action is covered (image uploads/reorders and drop create/update are not).

### PV-1 — Concurrent cancellation / double restock

1. **Original severity:** MEDIUM (potential).
2. **Root cause:** `restock` used a stale `returnedQty` loaded before the variant lock; `Order` had no version.
3. **Files modified:** `order/OrderService.java`, `order/Order.java`, `returns/ReturnService.java`, `returns/CustomerOrders.java`, `db/migration/V10__security_hardening.sql`; **new** `order/OrderLocks.java`.
4. **Fix:** `OrderLocks.lock` refreshes the order and its lines with `SELECT … FOR UPDATE` (a current read, correct under REPEATABLE READ) before any status check. It is taken in `changeStatus`, customer/guest cancel, auto-cancel, and in return create/status (which also locks the return row so two staff cannot reserve an exchange size twice). `Order` also gets `@Version` as a backstop.
5. **Regression tests:** `OrderServiceTest` (lock before status; a second concurrent cancel that finds the order already cancelled does nothing and publishes no event; customer cancel refused if staff confirmed meanwhile; auto-cancel refuses an order confirmed meanwhile), `CustomerOrdersAndLocksTest` (return create/changeStatus lock order then row, in order). Integration `concurrentCancelsRestockExactlyOnce` (6 parallel cancels → stock restored exactly once) written, **not run**.
6. **Actual results:** unit tests pass (mocked lock). Real concurrency **unverified**.
7. **Remaining limitations:** needs the integration test run against MySQL. Other order writers not touched here (`markPaid`) do not lock.

### PV-2 — Proxy trust / rate-limit bypass
Fixed together with VOID-02 (section above): `ClientIpResolver`, `forward-headers-strategy: none`, `void.trusted-proxies` / `TRUSTED_PROXIES`, deployment note in `docs/DEPLOY.md`. Tests: `ClientIpResolverTest` (8) plus the spoofing tests in `AuthControllerLoginLimitTest` and `AuditLogTest`. Remaining: platform-proxy behaviour and exact `TRUSTED_PROXIES` range must be confirmed per deployment.

### PV-3 — Missing input length limits
1. **Original severity:** MEDIUM (potential). 2. **Root cause:** unbounded e-mail/password on login and several free-text request fields. 3. **Files:** `auth/AuthController.java`, `order/OrderDtos.java`, `returns/ReturnDtos.java`. 4. **Fix:** `@Size` on login e-mail (190) and password (100), forgot (e-mail 190, lang 5), reset token (128), order/quote fields (slug 120, size 8, governorate 40, coupon 40, e-mail 190, phone 32, payment 16, lang 5), return type/reason; limiter map bounded. 5. **Tests:** `AuthControllerLoginLimitTest.oversizedAuthFieldsAreRejectedByValidation`, `OrderControllerTest.quoteRequestFieldsAreLengthLimited`, `RateLimiterTest` (bounded map). 6. **Results:** pass. 7. **Remaining:** no overall JSON body-size cap in the app (Caddy allows 40 MB for uploads); other admin-only fields keep their existing limits.

### PV-4 — Unverified COD orders reserving scarce inventory
1. **Original severity:** MEDIUM (potential). 2. **Root cause:** anonymous checkout reserves stock until a human acts. 3. **Files:** `order/OrderService.java`, `order/OrderRepository.java`, `content/SettingsService.java`, `V10__security_hardening.sql`, frontend `admin/pages/Content.tsx`, `admin/i18n.tsx`, `lib/errors.ts`, `i18n/strings.ts`; **new** `order/StaleOrderJob.java`. 4. **Fix:** max 10 pieces per order; max 2 unconfirmed orders per phone and per e-mail (staff exempt; limit configurable via `void.orders.max-open-per-customer`); a scheduled job cancels `NEW` orders older than `unconfirmed_order_hours` (default 72, editable in Admin → Content, 0 = off), each in its own transaction under the order lock, restocking and e-mailing the customer; plus the improved IP limits above. 5. **Tests:** `OrderServiceTest` (piece cap, open-order cap by phone and by e-mail, staff exemption, auto-cancel stale / fresh / confirmed meanwhile), `StaleOrderJobTest` (3), integration `unconfirmedOrdersAreCancelledAutomaticallyAndRestocked`, `anOrderCannotHoldMoreThanTenPieces` (not run). 6. **Results:** unit pass; integration not run. 7. **Remaining:** this reduces but does not stop a determined attacker (rotating phones/e-mails still reserves stock for up to 72 h); real prevention needs phone OTP, CAPTCHA/Turnstile, or a deposit. The 2-order / 10-piece / 72 h values are my choices; adjust to the business.

### PV-5 — Shipping-zone validation
1. **Original severity:** LOW (potential). 2. **Root cause:** `List<@Valid ZoneReq>` on the request body was not validated. 3. **Files:** `admin/AdminStoreController.java`. 4. **Fix:** list size 1–40 enforced and every element validated with the injected `Validator` before anything is changed (fee 0 – 10 000.00 EGP, ETA ≤ 16 chars, id required); the “fee required to enable” rule stays. 5. **Tests:** `AdminStoreControllerZonesTest` (5: absurd/negative fee, long ETA, null id, empty/null/oversized list, nothing applied on rejection, valid list applied, enable-without-fee). Integration `shippingZonesAreValidated` (not run). 6. **Results:** 5 / 5 pass. 7. **Remaining:** none known beyond the missing integration run.

---

## 3. Complete list of changed files

**Backend main** — `config/`: `SecurityConfig.java` (edited), `JwtSecretPolicy.java`, `JwtSecretStartupCheck.java` (new); `auth/`: `AuthController.java`, `AuthService.java`, `RateLimiter.java` (edited), `ClientIpResolver.java`, `AuditLog.java` (new); `order/`: `Order.java`, `OrderController.java`, `OrderDtos.java`, `OrderEmails.java`, `OrderRepository.java`, `OrderService.java`, `CouponRedemption.java`, `CouponRedemptionRepository.java` (edited), `OrderLocks.java`, `TrackingTokens.java`, `CustomerIdentity.java`, `StaleOrderJob.java` (new); `returns/`: `CustomerOrders.java`, `ReturnDtos.java`, `ReturnEmails.java`, `ReturnService.java`, `AdminReturnController.java` (edited); `admin/`: `AdminStoreController.java`, `AdminOrderController.java`, `AdminCatalogController.java` (edited); `waitlist/AdminWaitlistController.java`, `waitlist/WaitlistController.java`, `contact/AdminContactController.java`, `contact/ContactController.java`, `content/SettingsService.java`, `common/GlobalExceptionHandler.java` (edited). Resources: `application.yml` (edited), `db/migration/V10__security_hardening.sql`, `META-INF/spring.factories` (new).

**Backend tests (all new unless noted)** — `config/JwtSecretPolicyTest`, `config/SecurityConfigJwtTest`, `config/StartupSecretCheckTest`, `auth/ClientIpResolverTest`, `auth/RateLimiterTest`, `auth/AuthControllerLoginLimitTest`, `auth/AuditLogTest`, `order/OrderControllerTest`, `order/OrderServiceTest`, `order/StaleOrderJobTest`, `order/TrackingAndIdentityTest`, `returns/CustomerOrdersAndLocksTest`, `admin/AdminStoreControllerZonesTest`; `StoreFlowTests.java` (edited: new JWT secret, token flows, coupon assertions, new integration tests).

**Frontend** — `api/index.ts`, `api/types.ts`, `hooks/useQuote.ts`, `pages/TrackPage.tsx`, `pages/OrderPlacedPage.tsx`, `pages/CheckoutPage.tsx`, `lib/errors.ts`, `i18n/strings.ts`, `admin/pages/Content.tsx`, `admin/i18n.tsx`.

**Docs / config** — `README.md`, `docs/DEPLOY.md`, `.env.prod.example`, `backend/.env.example`, `SECURITY_FINDINGS.md` (status note added). Build artefacts `backend/target/` and `frontend/dist/` were regenerated (both git-ignored).

## 4. Not addressed (outside the requested scope)
VOID-07 to VOID-21 from the audit are untouched, including `COOKIE_SECURE` default (VOID-13), CSP (VOID-08), the dev-compose port exposure (VOID-09), the request-matcher asymmetry (VOID-10), the vulnerable frontend dependencies (VOID-15) and the container hardening items (VOID-16).
