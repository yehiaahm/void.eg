# VOID Store — Application Security Assessment

| | |
|---|---|
| **Target** | `void-store-handoff` (React 18 + Vite storefront/admin, Spring Boot 4.1.1 / Java 21 API, MySQL 8.4, Docker + Caddy) |
| **Date** | 2026-11-13 |
| **Type** | White-box review (SAST-style manual code review, SCA, secret scan, configuration review, threat modelling) plus isolated local validation harnesses |
| **Methodology references** | OWASP WSTG, OWASP ASVS 4.0, OWASP Top 10 (2021), OWASP API Security Top 10 (2023) |
| **Source modified?** | No. Nothing inside the project tree was changed by the assessment, apart from this report and `SECURITY_FINDINGS.md`. All scratch work (classpath file, two Java harnesses) lives in the session scratchpad. |

> **Read this first — limits of this assessment.**
> 1. **No live DAST was performed.** Docker Desktop's engine would not start in the assessment environment (`dockerDesktopLinuxEngine` pipe never appeared), so MySQL — and therefore the full application — could not be run. Nothing here was tested against a running server, a running Caddy, or a real deployment.
> 2. Every finding below is labelled **Confirmed** (code evidence, and a harness where noted), **Potential** (needs validation in a running environment) or **Informational**. Where a hypothesis was tested and turned out to be wrong, it is listed under *Hypotheses tested and disproved* so nobody re-chases it.
> 3. No automated SAST engine (Semgrep, SpotBugs, CodeQL) and no Maven SCA tool (OWASP Dependency-Check) was run; the backend review is manual. npm SCA was run (`npm audit`).
> 4. CVSS 4.0 scores are **hand-estimated** from the vector and are indicative only; they were not produced with the official calculator.
> 5. The assessed tree is **not a git repository**, so secret history, previous commits and CI configuration could not be examined. No `.github/`, GitLab CI or other pipeline files exist in the tree.

---

## 1. Executive Summary

**Overall assessment: requires security improvements before production.** The application is better built than most storefronts of this size. Prices are computed on the server, stock is locked with `SELECT … FOR UPDATE`, every database query is parameterised, tokens are stored hashed, refresh tokens rotate with reuse detection, admin endpoints carry both a URL rule and `@PreAuthorize`, and uploads are sniffed by magic bytes and renamed to content hashes. I found **no SQL injection, no stored/reflected/DOM XSS, no IDOR in the customer account area, and no way for a customer to change a price or role**.

The weaknesses cluster in three places: **(a)** a fail-open default for the token-signing secret, **(b)** abuse resistance of the anonymous endpoints (login, coupons, order tracking, checkout), and **(c)** absent audit logging.

**Overall risk rating: MEDIUM-HIGH** (HIGH if the app is ever started without `JWT_SECRET`; MEDIUM otherwise).

**Most critical findings**

| # | Finding | Severity |
|---|---|---|
| VOID-01 | If `JWT_SECRET` is unset the API silently signs tokens with a secret published in `application.yml`. A forged `role=OWNER` token was accepted by the app's own decoder configuration in a harness. | **HIGH** (conditional on deployment) |
| VOID-02 | Login throttling is per IP **and** email, so password spraying across many emails is unthrottled and a single account can be attacked from many IPs. | MEDIUM |
| VOID-03 | `/orders/quote` has no rate limit and reveals *why* a coupon code failed, so coupon codes can be enumerated. | MEDIUM |
| VOID-04 | Guest order tracking/cancellation/returns are protected only by a **sequential** order number plus the phone number. | MEDIUM |
| VOID-06 | No security event logging at all (logins, resets, role changes, PII exports). | MEDIUM |

**Main business risks:** takeover of the owner/admin account in a misconfigured deployment (full customer PII, ability to change orders and coupons); discount fraud (coupon guessing, per-customer limit bypass); disclosure of a customer's name/phone/address/email to someone who knows the customer's phone number; inability to detect or investigate an incident.

### Security Assessment Matrix

| Severity | Confirmed | Potential (needs validation) |
|---|---:|---:|
| **Critical** | 0 | 0 |
| **High** | 1 | 0 |
| **Medium** | 5 | 4 |
| **Low** | 9 | 1 |
| **Informational** | 6 | 0 |
| **Total** | **21** | **5** |

---

## 2. Scope, Architecture and Files Reviewed

### 2.1 Technology stack

| Layer | Technology |
|---|---|
| Storefront + admin | React 18.3, Vite 8.3, TypeScript 6, react-router-dom 6.30, TanStack Query 5 (admin is lazy-loaded at `/admin`) |
| API | Spring Boot 4.1.1, Java 21, Spring Security (stateless, `oauth2-resource-server` JWT, HS256), Spring Data JPA/Hibernate, Flyway, Jakarta Mail |
| Database | MySQL 8.4 (Flyway migrations V1–V9) |
| Runtime | One Docker image (frontend `dist/` packaged into the Spring Boot jar, non-root user), Caddy 2 reverse proxy with automatic HTTPS, MySQL container, uploads volume |
| Payments | Cash on delivery only. `PaymentProvider` interface is ready for Paymob but **no webhook or provider exists yet** |
| Third parties | SMTP provider (configurable), Google Fonts (loaded from the browser) |

### 2.2 Architecture and trust boundaries

```
Browser ──HTTPS──▶ Caddy (HSTS, nosniff, body ≤40 MB) ──▶ Spring Boot :8080 ──▶ MySQL
   │                                                        │  ├─ /api/v1/**  (JSON, Bearer JWT)
   │  access JWT in JS memory                                │  ├─ /uploads/** (static, volume)
   │  refresh token in httpOnly cookie                       │  ├─ /en|ar/**, /admin/** (SPA shell + SEO tags)
   └─ localStorage: bag, wishlist, language, 2 flags          │  └─ SMTP out (order/reset/contact mail)
```

Trust boundaries: anonymous internet → public API; authenticated customer → `/me/**`; staff (`STAFF` ⊂ `ADMIN` ⊂ `OWNER`) → `/admin/**`; app → MySQL; app → SMTP.

### 2.3 Files reviewed

**Read in full:** all of `config/`, `auth/`, `admin/`, `order/` (service, controller, DTOs, entities `Order`/`Coupon`, repository, e-mails, `Phones`), `returns/` (service, controllers, DTOs, e-mails, `CustomerOrders`), `user/` (`MeController`, `WishlistController`, `OwnerBootstrap`, `User`), `contact/`, `waitlist/` (controllers), `content/` (controller, `SettingsService`), `web/` (`SpaController`, `SitemapController`), `mail/`, `payment/`, `catalog/` (controller, DTOs), `common/`; `application.yml`, `pom.xml`, `Dockerfile`, both compose files, `Caddyfile`, `.env*.example`, `.gitignore`/`.dockerignore`, `run-all.bat`, `maven-wrapper.properties`; frontend `api/http.ts`, `admin/api.ts`, `InfoPage.tsx`, `About.tsx`, `index.html`, `vite.config.ts`, `package.json`.

**Reviewed by pattern search only (not read line by line):** remaining JPA entities and repositories (all `@Query`/`createQuery` call sites were enumerated and checked for parameterisation), `V1`–`V9` migrations (checked for credentials, grants, indexes), all other frontend components/pages/admin pages (searched for `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `localStorage`, `window.open`, `location.*`, `href=`, `navigate(`, token handling).

**Not reviewed, with reasons:** `node_modules/` (third-party; covered by `npm audit`), `frontend/dist/` (build output; grepped for secrets, none found), `design/` and `assets/` (static prototypes and images; excluded from the Docker image by `.dockerignore`), CSS files, `i18n` string tables, hero canvas animation (no security-relevant sinks), `mvnw`/`mvnw.cmd` scripts (standard Maven wrapper; only `maven-wrapper.properties` checked).

### 2.4 What was done well (verified in code)

- **Server-authoritative pricing** — unit price, discount and shipping are recomputed from the DB in `OrderService.place` (`OrderService.java:156-241`); the client sends only slug/size/qty.
- **Stock integrity** — variants locked in id order with `PESSIMISTIC_WRITE` (`OrderService.java:297-305`) plus `@Version` on `ProductVariant`.
- **Parameterised data access** — every JPQL query uses named parameters; there is no native SQL and no string-built query.
- **Token handling** — bcrypt cost 12, dummy-hash comparison for unknown e-mails (`AuthService.java:58`), refresh/reset tokens are 256-bit random and stored as SHA-256, refresh rotation with reuse-detection revoking all sessions (`AuthService.java:66-77`), reset revokes all refresh tokens (`AuthService.java:111`), `forgot` answers 204 either way, reset link built from configured `FRONTEND_URL`, not the `Host` header.
- **JWT validation** — algorithm pinned to HS256, issuer and expiry validated (`SecurityConfig.java:97-102`); refresh cookie is `HttpOnly; SameSite=Strict`, path-scoped to `/api/v1/auth`; access token never touches `localStorage`.
- **Authorization depth** — `/api/v1/admin/**` has a URL rule *and* per-method `@PreAuthorize`; ownership checks on every `/me/**` resource (`findByIdAndUserId`, `getUser().getId() == uid`); owner-only team management; self-demotion blocked.
- **Output encoding** — all e-mail HTML escapes user values (`EmailLayout.esc`); SEO tags escaped in `SpaController`; CSV export neutralises formula prefixes (`Csv.java:11-16`); React renders admin-authored page text as text nodes.
- **Uploads** — magic-byte sniffing, SVG not accepted, content-hash file names, 8 MB cap, path check on delete (`ImageStorage.java`).
- **Config hygiene** — secrets come from environment/`local.properties` (git-ignored); prod compose refuses to start without `JWT_SECRET`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `DOMAIN`; MySQL is not published in production; Actuator exposes only `health`; app container runs as non-root; no secrets found in the tree.

---

## 3. Technical Findings

Severity scale per the brief. CVSS 4.0 vectors are given with an **estimated** score.

### 3.1 Confirmed vulnerabilities

---

#### VOID-01 — Development JWT signing secret is the silent default in production
- **Severity:** HIGH (contextual — becomes CRITICAL if a deployment omits `JWT_SECRET`)
- **CVSS 4.0 (estimated):** `AV:N/AC:L/AT:P/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N` ≈ 9.0 when the default is live. `AT:P` because it needs a deployment mistake.
- **Affected:** `backend/src/main/resources/application.yml:59`; `backend/.../config/SecurityConfig.java:81-90`
- **Component:** every authenticated route (`/api/v1/me/**`, `/api/v1/admin/**`)
- **Technical explanation:** `void.jwt.secret` defaults to `dev-only-secret-change-me-0123456789abcdef0123456789`. `SecurityConfig.jwtKey` accepts any value ≥ 32 bytes and, for this value, only logs a warning (`SecurityConfig.java:86-88`). The key signs and verifies the access token (HS256), whose `role` claim drives authorization.
- **Root cause:** fail-open default; the safeguard is a log line instead of a startup failure. `docker-compose.prod.yml:31` enforces `JWT_SECRET` (`:?`), but the Dockerfile comments (`Dockerfile:30-31`) describe a Railway deployment path where nothing enforces it.
- **Attack scenario:** an operator deploys to a PaaS and forgets `JWT_SECRET`. The secret is in the public source, so anyone mints `{"iss":"void-api","sub":"1","role":"OWNER",…}` signed with it. `CurrentUser` and the `hasAnyRole` rules trust the claim; the admin API accepts the token (admin actions only look up the user to label the audit actor, `AdminOrderController.java:119-121`, `.orElse(null)`).
- **Business impact:** full admin takeover: read/export all customer PII, change order states, create coupons, alter staff.
- **Evidence:** harness `Harness2.java` (section B) used the same `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)` + `createDefaultWithIssuer("void-api")` configuration as `SecurityConfig.java:97-102`. Output: `forged token accepted by decoder: sub=1 role=OWNER`; control with a different secret: `rejected`. This confirms forgeability **given the default secret**; it does not show any real deployment uses it.
- **Safe reproduction:** run the harness only against synthetic keys, as above. Do not test against a deployed host. To check a deployment safely: confirm `JWT_SECRET` is set in its environment and that startup logs do not contain `Using the development JWT secret`.
- **Remediation:** remove the default from `application.yml`; fail startup when the secret is missing, short, or equals the dev marker unless an explicit `dev` profile is active.
- **Secure code:**
  ```java
  // SecurityConfig.jwtKey
  String secret = props.jwt().secret();
  boolean dev = Arrays.asList(env.getActiveProfiles()).contains("dev");
  if (secret == null || secret.getBytes(UTF_8).length < 32
          || (!dev && secret.contains("dev-only"))) {
      throw new IllegalStateException("JWT_SECRET must be set to >= 32 random bytes");
  }
  ```
  ```yaml
  void.jwt.secret: ${JWT_SECRET}      # no default
  ```
  Also set `COOKIE_SECURE` default to `true` (see VOID-13).

---

#### VOID-02 — Login throttling keyed on IP+email enables password spraying and distributed attack on one account
- **Severity:** MEDIUM — **CVSS 4.0 (est.):** `AV:N/AC:L/AT:N/PR:N/UI:N/VC:L/VI:L/VA:L/SC:N/SI:N/SA:N` ≈ 6.3
- **Affected:** `auth/AuthController.java:35` (`LoginReq`), `:66-72`; `auth/RateLimiter.java:23-42`
- **Endpoint:** `POST /api/v1/auth/login`
- **Technical explanation:** the limiter key is `"login:" + ip + ":" + email` with a budget of 8 per 15 min. Different e-mail ⇒ different bucket. There is no per-IP total, no per-account total across IPs, no lockout/back-off, no CAPTCHA.
- **Root cause:** a single composite key serves two incompatible goals.
- **Attack scenario:** (a) one IP tries one common password against every harvested customer e-mail (spraying); each e-mail sees one attempt, so nothing is ever limited. (b) a botnet attacks the owner's e-mail from many IPs at 8 tries/IP/15 min. (c) each attempt costs a bcrypt-12 verification (also for unknown e-mails, because of the dummy hash), so unthrottled distinct-e-mail traffic is a CPU-exhaustion vector.
- **Impact:** customer account takeover (saved addresses, order history); owner/admin brute force; CPU exhaustion.
- **Evidence:** code at the lines above. Not exercised dynamically (no running server).
- **Safe reproduction (local test stack only, synthetic accounts):** with 20 synthetic accounts, send one failed login for each from one source IP; observe that none returns `429`. Then send 9 attempts for one account from one IP and observe the 9th returns `429`.
- **Remediation:** add independent limits — per account (regardless of IP), per IP (regardless of e-mail), plus exponential back-off; consider CAPTCHA/Turnstile after N failures; cap field sizes (see PV-3).
- **Secure code:**
  ```java
  String email = User.normalizeEmail(req.email());
  limiter.check("login:ip:"   + ip,    30, Duration.ofMinutes(15));   // spraying
  limiter.check("login:acct:" + email,  10, Duration.ofMinutes(15));  // distributed attack on one account
  ```
  Use a bounded cache (Caffeine/Redis) for the limiter instead of an unbounded map.

---

#### VOID-03 — Coupon codes can be enumerated: unthrottled quote endpoint with distinguishing errors
- **Severity:** MEDIUM — **CVSS 4.0 (est.):** `AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:L/VA:L/SC:N/SI:N/SA:N` ≈ 5.3
- **Affected:** `order/OrderController.java:46-49`; `order/OrderService.java:107-119, 121-152`
- **Endpoint:** `POST /api/v1/orders/quote` (permit-all, **no** `limiter.check`)
- **Technical explanation:** the quote returns `couponError` ∈ {`invalid`, `not_started`, `expired`, `used_up`, `min_subtotal`, `already_used`}. A code that exists but is inactive/expired/used up answers differently from a non-existent one, and each request costs the server three or more queries.
- **Root cause:** convenience error detail on an anonymous, unmetered endpoint.
- **Attack scenario:** a script walks short guessable codes (`VOID10`, `DROP02`, `FRIENDS`…); responses reveal which exist, and a hit that returns `couponCode` echoes the live code with its discount.
- **Impact:** leaked influencer/private codes, revenue loss, cheap DB load amplification.
- **Evidence:** code. `OrderController.place` limits at 10/10 min (`:53`), `quote` does not.
- **Safe reproduction:** local stack with one synthetic coupon `TESTCODE`; call `/orders/quote` with `TESTCODE` and with `NOPE` and compare `couponError`; send 200 calls in a loop and confirm no `429`.
- **Remediation:** rate-limit `quote` per IP; return a single generic `invalid` for unauthenticated callers (give detail only after a code is accepted); use long random codes for private campaigns.
- **Secure code:** `limiter.check("quote:" + ip, 60, Duration.ofMinutes(5));` at the top of `quote`, and map every non-valid state to `invalid` in the response.

---

#### VOID-04 — Guest order tracking, cancellation and returns authenticated only by a sequential order number plus phone
- **Severity:** MEDIUM — **CVSS 4.0 (est.):** `AV:N/AC:L/AT:P/PR:N/UI:N/VC:L/VI:L/VA:N/SC:N/SI:N/SA:N` ≈ 5.5
- **Affected:** `returns/CustomerOrders.java:40-54`; `order/OrderController.java:63-85`; `order/OrderService.java:229` (number format); `order/OrderDtos.java:66-90` (response contents)
- **Endpoints:** `POST /api/v1/orders/track`, `/orders/cancel`, `/orders/returns`
- **Technical explanation:** the only secrets are the order number — `VOID-%06d` of `1000 + id`, i.e. sequential and guessable — and the customer's phone. A correct pair returns the full `OrderDto`: name, phone, e-mail, address, building, notes, items, totals. The same pair lets the caller cancel a `NEW` order or file a return.
- **Root cause:** a low-entropy identifier combined with a phone number that is not secret (phones are widely shared) and that cannot be rotated.
- **Attack scenario:** an attacker who knows a target's mobile number loops over order numbers within the observed range (the attacker's own order reveals the current maximum) at 20 tries/10 min per IP until one matches, obtaining the target's address and e-mail, then cancels pending orders to disrupt a limited drop. Rate limiting depends on a trustworthy client IP (PV-2).
- **Impact:** PII disclosure and integrity/availability impact on specific customers.
- **Evidence:** code at the lines above; `/cancel` shares the `track:` limiter bucket with `/track` (`OrderController.java:73`).
- **Safe reproduction:** local stack, two synthetic orders with different synthetic phones. Call `/orders/track` with order 1's number and order 2's phone → `404`; with the correct phone → full PII. Note the numbers increment by one.
- **Remediation:** add an unguessable per-order secret (random 128-bit token) delivered in the confirmation e-mail/track link and required together with the number; or use random non-sequential order numbers; return a minimum view to guests (mask phone/e-mail, street); per-order-number attempt limits.
- **Secure code (sketch):** store `trackingToken = Tokens.random()` on `Order`, put it in the e-mailed link, and compare in constant time (`MessageDigest.isEqual`) in `forGuest(number, phone, token)`.

---

#### VOID-05 — Per-customer coupon limit bypassed by changing the (unverified) e-mail
- **Severity:** MEDIUM — **CVSS 4.0 (est.):** `AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:L/VA:N/SC:N/SI:N/SA:N` ≈ 5.3
- **Affected:** `order/OrderService.java:114-116`; `order/OrderDtos.java:24-27`
- **Endpoint:** `POST /api/v1/orders`
- **Technical explanation:** `perCustomer` counts redemptions by the `contact.email` string the shopper typed. The e-mail is never verified, so `a+1@x.com`, `a+2@x.com`, … (or any new address) each get a fresh allowance. `quote` skips the check entirely when no e-mail is sent (`:114`).
- **Root cause:** trusting a client-supplied, unverified identity for a fraud control.
- **Impact:** repeated use of one-per-customer discounts, bounded only by `maxUses` and the checkout rate limit.
- **Evidence:** code. **Safe reproduction:** local stack, coupon with `perCustomer=1`; place two synthetic orders with `x+1@test.local` then `x+2@test.local`, both accepted.
- **Remediation:** key the control on something harder to rotate (verified account, phone, normalised address, payment instrument once online payment exists); strip `+tag` and dots for known providers; use single-use codes for one-per-customer campaigns.

---

#### VOID-06 — No security event logging or audit trail
- **Severity:** MEDIUM (OWASP A09) — CVSS not applicable (control gap)
- **Affected:** `auth/AuthController.java`, `auth/AuthService.java`, `admin/*`, `user/*` — a search for `log.` finds only `OwnerBootstrap` (`:37,41,50`); `common/GlobalExceptionHandler.java:46,84`
- **Technical explanation:** failed/successful logins, refresh-token reuse (which silently revokes all sessions, `AuthService.java:70-73`), password resets, role/enable changes (`AdminStoreController.java:107-116, 311-329`), staff creation, CSV exports (`AdminOrderController.java:173-195`, up to 10 000 orders of PII by the lowest role), coupon edits and settings changes are not logged. Rate-limit hits (`429`) are not logged either.
- **Impact:** a compromise (for example VOID-01 or VOID-02) cannot be detected or reconstructed; no evidence for insider-abuse disputes.
- **Evidence:** code search. **Remediation:** structured audit log (who, what, target, IP, result) to stdout for log shipping, alert on repeated login failures, token reuse, role changes and exports. Do not log credentials, tokens or full PII.
- **Secure code:** `log.warn("auth.login.failed email_hash={} ip={}", sha256(email), ip);` and the same pattern for `auth.refresh.reuse`, `staff.role.changed`, `orders.export`.

---

#### VOID-07 — Session lifecycle gaps: access tokens are not re-checked against the database; password change does not revoke sessions
- **Severity:** LOW — **CVSS 4.0 (est.):** `AV:N/AC:L/AT:P/PR:L/UI:N/VC:L/VI:L/VA:N/SC:N/SI:N/SA:N` ≈ 2.1
- **Affected:** `config/SecurityConfig.java:71-78`; `auth/CurrentUser.java:16-23`; `admin/AdminStoreController.java:114, 327`; `auth/AuthService.java:114-121`
- **Technical explanation:** `role` and `name` are read from the JWT. After a staff member is disabled or demoted, or a customer is disabled, the existing access token (default 15 min) keeps working; the code comment acknowledges "role changes take effect on next sign-in". `changePassword` updates the hash but leaves refresh tokens valid (unlike `resetPassword`, which revokes them).
- **Impact:** up to 15 minutes of residual access; a stolen refresh token survives the owner changing their password.
- **Remediation:** revoke all refresh tokens in `changePassword`; for privileged roles use a 5-minute access token or a `tokenVersion`/`passwordChangedAt` claim checked per request.

---

#### VOID-08 — No Content-Security-Policy
- **Severity:** LOW — **CVSS:** n/a (defence in depth)
- **Affected:** `deploy/Caddyfile:7-13`; `config/SecurityConfig.java:48-51`; `frontend/index.html`
- **Technical explanation:** Caddy adds HSTS, `nosniff`, `Referrer-Policy`, `Permissions-Policy`; Spring adds `X-Frame-Options: DENY`; no CSP is set anywhere. No XSS sink was found, so this is hardening, but a CSP would contain the damage of any future mistake, and the admin panel handles all customer PII.
- **Remediation:** add a CSP at Caddy. Note that `index.html` uses an inline `onload=` handler for font preloading, which a strict `script-src 'self'` would block; replace it with a small external script or `<link rel="stylesheet">`.
- **Secure code:**
  ```
  header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
  ```

---

#### VOID-09 — Dev compose publishes MySQL (root password) and Mailpit on all interfaces
- **Severity:** LOW (development only) — **CVSS 4.0 (est.):** `AV:A/AC:L/AT:P/PR:N/UI:N/VC:L/VI:L/VA:N` ≈ 2.0
- **Affected:** `docker-compose.yml:9-10, 12-13, 25-27`
- **Technical explanation:** `3307:3306`, `1025:1025`, `8025:8025` bind to `0.0.0.0` by default, with the well-known credentials `void_dev_password` / `void_dev_root`. Anyone on the same Wi-Fi/LAN can log into the dev database as root and read Mailpit, which contains password-reset links.
- **Remediation:** bind to loopback: `"127.0.0.1:3307:3306"`, `"127.0.0.1:8025:8025"`, `"127.0.0.1:1025:1025"`; never restore production data into the dev database.

---

#### VOID-10 — Request-matcher asymmetry in the filter chain (latent)
- **Severity:** LOW — **CVSS:** n/a (no exploitable impact today)
- **Affected:** `config/SecurityConfig.java:52-62`
- **Technical explanation:** the final catch-all uses `r -> !r.getRequestURI().startsWith("/api/")` → `permitAll`, comparing the **raw, un-decoded** URI, while the preceding path rules match the **decoded** path. A request to `/%61pi/v1/me` is routed by Spring MVC to `/api/v1/me`, but matches the catch-all and so skips `anyRequest().authenticated()`.
- **Why it is not exploitable today:** `/api/v1/admin/**` is matched earlier on the decoded path and is also guarded by `@PreAuthorize`; every `/me/**` handler calls `CurrentUser.require()`. A future controller that relies only on the filter chain would be exposed.
- **Evidence:** harness `Harness.java` (H1): `/%61pi/v1/admin/orders → admin-rule=true`; `/%61pi/v1/me → admin-rule=false, non-api-permitAll-rule=true`. The final dispatch of `/%61pi/...` through Tomcat + MVC was **not** exercised end-to-end.
- **Remediation:**
  ```java
  .requestMatchers("/api/v1/admin/**").hasAnyRole("STAFF", "ADMIN", "OWNER")
  .requestMatchers("/api/**").authenticated()   // path-pattern based, decoded
  .anyRequest().permitAll()
  ```

---

#### VOID-11 — Account enumeration at registration
- **Severity:** LOW — **CVSS 4.0 (est.):** ≈ 2.3 (`VC:L`)
- **Affected:** `auth/AuthService.java:48`; `auth/AuthController.java:59-63`
- **Technical explanation:** registering an existing e-mail returns `409 email_taken`, confirming the account exists. Login and password reset are correctly uniform. Limited to 10 registrations/IP/hour.
- **Remediation:** accept the registration generically and e-mail "you already have an account" to the address, or accept the residual risk and keep the rate limit.

---

#### VOID-12 — Waitlist entries can be overwritten by anyone who knows the phone number
- **Severity:** LOW — **CVSS 4.0 (est.):** ≈ 2.1 (`VI:L`)
- **Affected:** `waitlist/WaitlistController.java:40-47`
- **Technical explanation:** an unauthenticated sign-up for a phone number that has an un-contacted entry silently replaces that entry's name, e-mail and notes. Rate-limited to 5/10 min/IP.
- **Remediation:** append a new row (or only fill empty fields) instead of overwriting; de-duplicate in the admin view.

---

#### VOID-13 — Insecure defaults: refresh cookie `Secure` off, dev datasource with `allowPublicKeyRetrieval=true`
- **Severity:** LOW — **CVSS 4.0 (est.):** ≈ 2.4
- **Affected:** `application.yml:62` (`secure-cookie: ${COOKIE_SECURE:false}`), `application.yml:9-11`
- **Technical explanation:** the refresh cookie is `Secure` only when `COOKIE_SECURE=true`; the production compose sets it, a PaaS deployment may not. HSTS is added by Caddy only, so on a path without Caddy the cookie can travel over HTTP. The default JDBC URL enables `allowPublicKeyRetrieval=true` and the default DB password is `void_dev_password`; they apply only when `DB_*`/`MYSQL*` variables are absent.
- **Remediation:** default `secure-cookie` to `true` and set `false` only under a `dev` profile; drop the dev DB defaults from the base config; set HSTS in Spring too.

---

#### VOID-14 — Weak customer password policy
- **Severity:** LOW — **CVSS:** n/a
- **Affected:** `auth/AuthController.java:32, 39`; `user/MeController.java:40` (staff passwords require ≥ 10: `AdminStoreController.java:282`)
- **Technical explanation:** customers need only 8 characters, with no breached-password or common-password check. Combined with VOID-02 this raises takeover risk.
- **Remediation:** minimum 10–12 characters, reject top-N common passwords or check against a breached-password list, keep the 100-character cap.

---

#### VOID-15 — Vulnerable frontend dependencies (practical impact low)
- **Severity:** LOW — **CVSS:** per advisory (react-router 6.1 CVSS 3.1 for `deserializeErrors`)
- **Affected:** `frontend/package.json:16` (`react-router-dom ^6.30.6`), `:25` (`vite ^8.3.0`)
- **Evidence:** `npm audit` → 0 critical, 1 high, 2 moderate.
  - `vite` 8.3.0–8.3.2 (high): transitive `launch-editor` command-handling advisories (GHSA-698v-4r3x-3vcv, -r825-w8mm-5pxw, -587h-7wmj-h5pm). **Dev server only**; the Docker image runs `npm run build` and ships `dist/` only.
  - `react-router(-dom)` 6.0–7.17 (moderate): open redirect via backslash in `<Link>`/`navigate` (GHSA-wrjc-x8rr-h8h6) and `deserializeErrors` SSR hydration (GHSA-337j-9hxr-rhxg). The app has no SSR, and no `navigate`/`Link` target is derived from user input (`AccountPage.tsx:33-35` compares `next` to a literal), so neither is reachable as used.
- **Remediation:** upgrade Vite to a patched 8.3.x+; plan the react-router move to the fixed 7.x line. Backend SCA still needs `dependency-check` or Dependabot/Renovate (Spring Boot 4.1.1 BOM was current at review time but was not scanned).

---

#### VOID-16 to VOID-21 — Informational

| ID | Observation | Where | Recommendation |
|---|---|---|---|
| **VOID-16** | Container hardening: base images use floating tags (`node:22-alpine`, `maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre-alpine`, `mysql:8.4`, `caddy:2-alpine`, `axllent/mailpit:latest`); no `read_only`, `cap_drop`, `no-new-privileges`, or memory/CPU limits; secrets are passed as environment variables; MySQL health check puts the password on the command line. Positives: app runs as non-root, MySQL unpublished. | `Dockerfile:5,13,22`; `docker-compose.prod.yml:5,48`; `docker-compose.yml:23` | Pin by digest; add `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, resource limits; use Docker secrets/`_FILE`. |
| **VOID-17** | `POST /admin/staff` for an e-mail that already belongs to a **customer** promotes that account and **replaces its password hash**. Owner-only, but silently takes over a customer's login and merges their order history into a staff identity. | `admin/AdminStoreController.java:299-308` | Require a distinct staff e-mail, or keep the customer's password and force a reset. |
| **VOID-18** | Insider-risk controls: any STAFF account can export up to 10 000 orders with PII (`AdminOrderController.java:173-195`), approve returns and record an arbitrary refund amount (`AdminReturnController.java:42`, `ReturnService.java:137-140`), mark orders delivered/paid. Intended by the role design, but there is no dual control, cap or audit (see VOID-06). | as listed | Restrict export to ADMIN, cap refunds to item value, add audit events. |
| **VOID-19** | Fonts load from Google (`fonts.googleapis.com`, `fonts.gstatic.com`): visitor IPs go to a third party (privacy; also affects the CSP). | `frontend/index.html:19-31` | Self-host the fonts. |
| **VOID-20** | `mailto:` links are built from visitor-supplied e-mail (`Messages.tsx:100,116`, `Waitlist.tsx:133`); `@Email` allows `?` in the local part, so a crafted address could add a `bcc=` parameter when staff click it. `contact_email` is not format-validated, only `*_url` keys are (`AdminStoreController.java:238-242`). | as listed | Validate against a strict e-mail regex before use in `href`; validate `contact_email`. |
| **VOID-21** | Dev tooling: `run-all.bat:42` generates the local owner password and JWT secret with PowerShell `Get-Random` (non-cryptographic, time-seeded in Windows PowerShell 5.1); Maven wrapper has no `distributionSha256Sum` (`maven-wrapper.properties`); the tree is not under version control so history cannot be audited. | as listed | Use `RandomNumberGenerator`; add the checksum; initialise git and scan history with gitleaks/trufflehog. |

---

### 3.2 Potential vulnerabilities requiring validation

| ID | Severity if confirmed | Finding | Where | How to validate safely |
|---|---|---|---|---|
| **PV-1** | MEDIUM | **Concurrent double-cancel could inflate stock.** `Order` has no `@Version` (only `ProductVariant` does). `cancelByCustomer`/`guestCancel` → `changeStatus` → `restock` read `returnedQty` from an entity loaded before the variant row is locked. Two simultaneous cancels of the same `NEW` order might each add `qty − returnedQty` back to stock, so a shopper could mint inventory and oversell a limited drop. With a coupon, the second transaction may instead fail on deleting an already-deleted redemption, which would mask it. | `OrderService.java:276-291, 307-322`; `CustomerOrders.java:46-54`; `Order.java` (no `@Version`) | On a Testcontainers/local stack: place one synthetic order (qty 1), record stock, send two simultaneous `POST /orders/cancel`, compare stock before/after. The existing `StoreFlowTests` covers concurrent *orders*, not concurrent cancels. |
| **PV-2** | MEDIUM | **Rate limits trust the left-most `X-Forwarded-For`.** `forward-headers-strategy: framework` makes `getRemoteAddr()` return the **first** XFF entry (harness A: `XFF='198.51.100.77, 203.0.113.9'` → `198.51.100.77`). Caddy's `reverse_proxy` does not trust incoming XFF by default, so the compose deployment should be safe. If the app port is reachable directly, or a front proxy *appends* to a client-supplied header (Railway is mentioned at `Dockerfile:30-31`), every limiter (`login`, `register`, `forgot`, `order`, `track`, `contact`, `waitlist`) can be reset per request by rotating the header. | `application.yml:45`; `RateLimiter.java`; all `limiter.check(..., http.getRemoteAddr())` calls | Against a running deployment, send two requests with different spoofed `X-Forwarded-For` values from the same host and compare limiter behaviour. Fix: use `forward-headers-strategy: native` with Tomcat `RemoteIpValve` + `internal-proxies`, or configure the platform to overwrite XFF. |
| **PV-3** | MEDIUM | **Unbounded login field sizes feed the limiter map.** `LoginReq.email` has no `@Size` and `password` no length cap; the e-mail is embedded in the limiter key (`AuthController.java:67`), and entries live for up to an hour in an unbounded `ConcurrentHashMap` (`RateLimiter.java:23, 48-56`). Large unique e-mails could grow heap and bcrypt is run on arbitrarily long passwords. | `AuthController.java:35, 66-70`; `RateLimiter.java` | In a local stack, send a few thousand unique 100 KB e-mails and watch heap. Fix: `@Size(max=190)` on e-mail and `@Size(max=100)` on password; bounded cache. |
| **PV-4** | MEDIUM | **Inventory denial of limited drops.** Guest COD checkout needs no verification: 10 orders/10 min/IP, up to 30 lines × 10 pieces each (`OrderService.java:156-241`, `OrderController.java:53`) reserve stock that is only released when staff cancel. A bot (or PV-2 header rotation) can sell out a drop with fake orders. | as listed | Business-logic test on a staging stack with synthetic data only. Fix: phone/e-mail verification (OTP), CAPTCHA, per-phone and per-address caps, auto-cancel unconfirmed COD orders after N hours. |
| **PV-5** | LOW | **List-element validation may not run.** `saveZones(@RequestBody List<@Valid ZoneReq> reqs)` has no `@Valid`/`@Validated` on the parameter or class, so the `@Min(0) @Max(10_000_00)` bounds on `fee` might not be enforced. Admin-only. | `AdminStoreController.java:211-225` | Post a fee of `2147483647` on a local stack and see whether it is rejected. Fix: annotate the controller with `@Validated`. |

### 3.3 Hypotheses tested and disproved (no action needed)

| Hypothesis | Result |
|---|---|
| All clients share one rate-limit bucket behind Caddy because `getRemoteAddr()` is the proxy | **Disproved.** Spring 7 `ForwardedHeaderFilter` rewrites the remote address from XFF (harness H2: `203.0.113.9`). The real concern is XFF trust (PV-2). |
| Header injection through the contact-form name into the e-mail `Subject` | **Disproved.** The subject always contains a non-ASCII character, so Jakarta Mail RFC 2047-encodes the whole value; CR/LF stayed inside the encoded word and no `Bcc:` header was produced (harness C). |
| SQL injection | **Not found.** All JPQL is parameterised; no native SQL. |
| Stored / reflected / DOM XSS | **Not found.** React text nodes for all admin- and visitor-supplied text; the only `dangerouslySetInnerHTML` sinks render static, trusted SVG from `productArt.ts`; e-mail and SEO HTML escaped server-side. |
| IDOR on `/me/**` orders, addresses, returns | **Not found.** Ownership enforced in each handler. |
| Price, total or role tampering | **Not found.** Server recomputes every amount; role comes from a signed claim; mass assignment impossible with record DTOs. |
| JWT algorithm confusion / `none` | **Not found.** HS256 pinned; issuer and expiry validated. |
| CSRF | **Not applicable.** Bearer tokens + `SameSite=Strict` path-scoped refresh cookie. |
| Upload abuse (type confusion, traversal, SVG) | **Not found.** Magic bytes, hash names, `nosniff`, 8 MB cap. |
| CSV/formula injection in exports | **Not found.** `Csv.csv` prefixes `' ` for `= + - @ TAB CR`. |
| Open redirect / `redirectUrl` abuse | **Not found.** `redirectUrl` is typed but never navigated to; `next` is compared to a literal. |
| Secrets in source / build output | **Not found** (pattern scan of the tree and `dist/`; the tree has no git history to check). |

---

## 4. Attack Surface Analysis

### 4.1 Public endpoints (no authentication)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/products`, `/api/v1/products/{slug}` | Catalogue; stock capped at 10 for display |
| GET | `/api/v1/settings`, `/api/v1/pages/{slug}`, `/api/v1/shipping-zones` | Public store content; settings include contact e-mail/phone |
| GET | `/api/v1/sitemap.xml`, `/sitemap.xml`, `/robots.txt` | |
| POST | `/api/v1/orders/quote` | **no rate limit** (VOID-03) |
| POST | `/api/v1/orders` | 10 / 10 min / IP; blocked when store closed for non-staff |
| POST | `/api/v1/orders/track`, `/orders/cancel`, `/orders/returns` | number + phone (VOID-04); 20 or 10 / 10 min / IP |
| POST | `/api/v1/waitlist`, `/api/v1/contact` | 5 / 10 min / IP |
| GET | `/uploads/**`, `/actuator/health`, `/error` | static images; health only |
| GET | `/en/**`, `/ar/**`, `/admin/**`, `/` | SPA shell with server-rendered SEO tags (HTML-escaped) |

### 4.2 Authentication entry points

`POST /api/v1/auth/register` (10/h/IP), `/login` (8/15 min/IP+email — VOID-02), `/refresh` (cookie, rotating), `/logout`, `/forgot` (5/h/IP), `/reset` (10/h/IP). First owner is created at start-up from `OWNER_EMAIL`/`OWNER_PASSWORD` (`OwnerBootstrap`).

### 4.3 Authenticated customer endpoints (`CUSTOMER` and up)

`GET/PATCH /api/v1/me`, `POST /me/password`, `GET/POST/PUT/DELETE /me/addresses[/{id}]`, `GET /me/orders[/{number}]`, `POST /me/orders/{number}/returns`, `POST /me/orders/{number}/cancel`, `GET/PUT/DELETE/POST /me/wishlist[...]`.

### 4.4 Admin routes (`/api/v1/admin/**`, URL rule + `@PreAuthorize`)

| Role | Capabilities |
|---|---|
| **STAFF** | dashboard; list/read orders, change order status, notes, edit address, **export orders CSV**; read products, edit stock; read drops, customers, shipping zones; returns list/read/**status + refund amount**; store status read; waitlist list/contacted/**CSV export**; contact messages list/handled |
| **ADMIN** | product create/update/delete + image upload/edit/reorder/delete; drop create/update/delete; enable/disable customers; coupons CRUD; shipping zones write; settings read/write (whitelisted keys); pages write; open/close store; delete waitlist entries and messages |
| **OWNER** | staff list/create/update (roles STAFF/ADMIN only) |

Front-end admin UI: `/admin/**` (lazy bundle, `noindex`, listed in `robots.txt`).

### 4.5 Sensitive data flows

| Data | Path | Protection observed |
|---|---|---|
| Customer name, phone, e-mail, address | checkout → MySQL → e-mail (SMTP) → admin UI / CSV | TLS at Caddy; DB not published in prod; **no audit trail** (VOID-06); guest view exposes it with number+phone (VOID-04) |
| Passwords | register/login → bcrypt-12 in `users` | good |
| Refresh / reset tokens | cookie / e-mail → SHA-256 in DB | good |
| Access JWT | memory only, 15 min | good; secret default risk (VOID-01) |
| Contact messages, waitlist | public form → DB → admin | rate-limited; stored as text, rendered as text |
| Uploads | admin → `/data/uploads` volume → public `/uploads/**` | sniffed, hash-named |
| Secrets | `.env` / env vars / `local.properties` | git-ignored; no secrets in tree |

---

## 5. Remediation Roadmap

### Immediate (before any production exposure)
1. **VOID-01** — remove the JWT default, fail startup on missing/dev secret; verify every deployment target sets `JWT_SECRET` (≥ 48 random bytes) and `COOKIE_SECURE=true`.
2. **PV-2** — decide the proxy trust model (Caddy overwriting XFF, or `native` strategy with `internal-proxies`); make sure the app port is never directly reachable.

### Priority (next release)
3. **VOID-02 / PV-3** — layered login limits (per IP, per account, back-off), field size caps, bounded limiter store.
4. **VOID-03** — rate-limit `/orders/quote`, generic coupon error for anonymous callers.
5. **VOID-04** — unguessable per-order tracking token or random order numbers; reduce what guests see.
6. **VOID-06** — structured audit logging for auth, role changes, exports and 429s.
7. **PV-1** — add `@Version` (or lock the order row) in the cancel/restock path and add a concurrent-cancel test.

### Short-term
8. **VOID-05**, **PV-4** — verified identity for per-customer coupons; OTP/CAPTCHA and auto-cancel for unconfirmed COD orders.
9. **VOID-07**, **VOID-14** — revoke sessions on password change; stronger password policy; shorter privileged tokens.
10. **VOID-08**, **VOID-13**, **VOID-10** — CSP, secure-by-default cookie, matcher reorder.
11. **VOID-15** — upgrade Vite and react-router; add backend SCA to CI.

### Long term
12. Put the project in version control, add CI with secret scanning, SAST (Semgrep/CodeQL), SCA (Dependabot or Dependency-Check) and an image scan (Trivy); pin images by digest (**VOID-16**, **VOID-21**).
13. Before enabling Paymob: HMAC-verify webhooks, make `markPaid` idempotent and amount-checked, use test mode first, keep keys out of the image.
14. Admin MFA (TOTP) for ADMIN/OWNER; Cloudflare Turnstile on login/checkout/contact; edge rate limiting; periodic restore-tested backups (procedure in `docs/DEPLOY.md`).
15. Run a proper DAST pass (ZAP/Burp) against a staging stack with synthetic data once Docker or a hosted staging is available — this is the main gap in this assessment.

---

## 6. Final Assessment

**Requires security improvements before production.**

The core money paths and data access are sound, and I found no flaws that let an anonymous visitor read the database, execute script in another user's session, or change a price. That said, the application should not go live until VOID-01 is closed and the proxy trust model (PV-2) is settled, because together they decide whether the rate limits and the admin boundary hold in the real deployment. The Medium items (VOID-02 to VOID-06) are realistic abuse paths for a limited-drop store and should be fixed in the first release.

This is **not** a statement that the application is secure. The review was static plus isolated harnesses; no running instance, no real network path, no real mail server, no browser and no production configuration was tested, and the backend was not scanned by automated SAST/SCA tools.
