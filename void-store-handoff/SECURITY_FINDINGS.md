# VOID Store — Security Findings (prioritised)

Full detail, evidence and remediation code: [SECURITY_AUDIT_REPORT.md](SECURITY_AUDIT_REPORT.md). Assessed 2026-11-13.

> **Remediation status:** VOID-01 to VOID-06 and PV-1 to PV-5 have been addressed in code; see [SECURITY_REMEDIATION_REPORT.md](SECURITY_REMEDIATION_REPORT.md) for what was changed, what was tested and what still needs a running environment. The tables below are the original findings and are not updated.

**Verdict: requires security improvements before production.** Static review plus isolated harnesses only. No live DAST was possible (Docker engine would not start), so nothing was tested against a running server. CVSS 4.0 scores are hand-estimated.

| Severity | Confirmed | Potential |
|---|---:|---:|
| Critical | 0 | 0 |
| High | 1 | 0 |
| Medium | 5 | 4 |
| Low | 9 | 1 |
| Info | 6 | 0 |

## Confirmed

| ID | Sev | Finding | Location | Fix |
|---|---|---|---|---|
| VOID-01 | **HIGH** | Default dev JWT secret used silently if `JWT_SECRET` is unset; forged `role=OWNER` token accepted by the app's decoder config (harness) | `application.yml:59`, `SecurityConfig.java:81-90` | Remove default, fail startup on missing/dev secret |
| VOID-02 | MED | Login limiter keyed IP+email: password spraying and distributed attack on one account unthrottled; bcrypt CPU burn | `AuthController.java:35,66-72`, `RateLimiter.java` | Separate per-IP and per-account limits, back-off |
| VOID-03 | MED | `/orders/quote` unthrottled and reveals why a coupon failed, so codes can be enumerated | `OrderController.java:46-49`, `OrderService.java:107-152` | Rate-limit, generic error |
| VOID-04 | MED | Guest track/cancel/return protected only by sequential order number + phone; returns full PII | `CustomerOrders.java:40-54`, `OrderService.java:229` | Per-order secret token / random numbers, minimal guest view |
| VOID-05 | MED | Per-customer coupon limit bypassed by changing unverified e-mail | `OrderService.java:114-116` | Verified identity, single-use codes |
| VOID-06 | MED | No security audit logging (logins, reuse, role changes, PII exports) | `auth/*`, `admin/*` | Structured audit log + alerts |
| VOID-07 | LOW | Access JWT not re-checked vs DB; password change keeps refresh tokens | `SecurityConfig.java:71-78`, `AuthService.java:114-121` | Revoke on change, token version |
| VOID-08 | LOW | No Content-Security-Policy | `deploy/Caddyfile:7-13` | Add CSP (fix inline `onload` in `index.html`) |
| VOID-09 | LOW | Dev compose publishes MySQL (root pw) and Mailpit on all interfaces | `docker-compose.yml:9-13,25-27` | Bind to 127.0.0.1 |
| VOID-10 | LOW | Raw-URI catch-all vs decoded path rules (`/%61pi/...`); latent, not exploitable today | `SecurityConfig.java:52-62` | `/api/**` authenticated, then permitAll |
| VOID-11 | LOW | Registration reveals existing e-mails (409) | `AuthService.java:48` | Generic response |
| VOID-12 | LOW | Waitlist entry overwritten by anyone with the phone number | `WaitlistController.java:40-47` | Append, don't overwrite |
| VOID-13 | LOW | `COOKIE_SECURE` defaults false; dev DB defaults in base config | `application.yml:9-11,62` | Secure by default |
| VOID-14 | LOW | Customer password min 8, no common/breached check | `AuthController.java:32,39` | 10–12 + blocklist |
| VOID-15 | LOW | `npm audit`: vite (high, dev-only), react-router (moderate, not reachable as used) | `package.json:16,25` | Upgrade; add backend SCA |
| VOID-16 | INFO | Floating image tags, no container hardening flags | `Dockerfile`, compose files | Pin digests, cap_drop, limits |
| VOID-17 | INFO | Owner "create staff" overwrites an existing customer's password | `AdminStoreController.java:299-308` | Require new e-mail |
| VOID-18 | INFO | STAFF can export 10k orders and record arbitrary refunds, no audit | `AdminOrderController.java:173-195` | Restrict/cap/audit |
| VOID-19 | INFO | Google Fonts leak visitor IPs, complicate CSP | `index.html:19-31` | Self-host |
| VOID-20 | INFO | `mailto:` from visitor e-mail (`?` allowed); `contact_email` unvalidated | `Messages.tsx:100,116` | Strict validation |
| VOID-21 | INFO | `Get-Random` for dev secrets, no Maven wrapper checksum, no VCS | `run-all.bat:42` | CSPRNG, checksum, git + secret scan |

## Potential (needs validation in a running environment)

| ID | Sev | Finding | Validate by |
|---|---|---|---|
| PV-1 | MED | Concurrent double-cancel may double-restock (no `@Version` on `Order`) | Two parallel cancels on a local stack, compare stock |
| PV-2 | MED | Limiters trust left-most `X-Forwarded-For` (harness); safe with Caddy default, not if port exposed or proxy appends | Send spoofed XFF to the deployment |
| PV-3 | MED | Unbounded login e-mail/password length feeds limiter map and bcrypt | Many large unique e-mails, watch heap |
| PV-4 | MED | Unverified COD checkout can hoard stock of limited drops | Staging test with synthetic data |
| PV-5 | LOW | `List<@Valid ZoneReq>` may skip validation in `saveZones` | Post an out-of-range fee locally |

## Tested and disproved
Shared rate-limit bucket behind Caddy; mail Subject header injection; SQL injection; XSS (stored/reflected/DOM); IDOR on `/me/**`; price/role tampering; JWT alg confusion; CSRF; upload abuse; CSV formula injection; open redirect; secrets in source/dist.

## Fix order
1. VOID-01, PV-2 (before any exposure) 2. VOID-02/03/04/06, PV-1, PV-3 3. VOID-05, PV-4, VOID-07/14 4. VOID-08/10/13/15 5. CI with SAST/SCA/secret scanning, then a real DAST pass on staging.
