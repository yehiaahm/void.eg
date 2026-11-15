# Deploying VOID

The production stack is three containers on one server: **Caddy** (HTTPS) → **app** (Spring Boot serving the API, the storefront and the admin) → **MySQL**. Product photos live in a Docker volume.

## 1. Server

- Any Linux VPS with 2 GB RAM or more (e.g. Hetzner, DigitalOcean, Contabo). Ubuntu 24.04 is a good default.
- Install Docker: `curl -fsSL https://get.docker.com | sh`
- Point the domain's DNS **A record** (and `www`) at the server's IP. Open ports 80 and 443.

## 2. Configure

```bash
git clone <your repo> void && cd void
cp .env.prod.example .env
nano .env
```

Fill in every value. Generate secrets with `openssl rand -base64 48`. **The API will not start** if `JWT_SECRET` is missing, shorter than 32 bytes, a placeholder (for example anything copied from the examples) or not random enough. There is no built-in default. This also applies to any other platform you deploy to (Railway etc.): set `JWT_SECRET` and `COOKIE_SECURE=true` before the first start.

- `DOMAIN` is the bare domain (no `https://`).
- `MAIL_*` is any SMTP provider (Zoho Mail, Google Workspace, Brevo, Resend…). Without it the store still works, but no emails are sent.
- `OWNER_EMAIL` / `OWNER_PASSWORD` creates the first admin account on first start. After that, manage the team from Admin → Staff.

## 3. Start

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f app     # wait for "Started VoidApiApplication"
```

Caddy gets the HTTPS certificate automatically. Open `https://your-domain/admin` and sign in.

### Reverse proxy and client IPs

Rate limits and the audit log identify visitors by IP address. The app only believes `X-Forwarded-For` when the TCP peer is a *trusted proxy* (`TRUSTED_PROXIES`, a comma-separated CIDR list; default = loopback and private networks, which covers the Caddy container). A request that reaches the app directly from the internet has its forwarding header ignored. Keep the app port unpublished (as in `docker-compose.prod.yml`) and, if other private hosts can reach the app, narrow `TRUSTED_PROXIES` to the proxy's exact address range. On a platform proxy (Railway, Render…) make sure it overwrites or appends the real client address in `X-Forwarded-For`.

## 4. Update to a new version

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

`V10__security_hardening.sql` adds an order version column, extra coupon-redemption identity columns (phone, account, address hash) and the `unconfirmed_order_hours` setting. Take a database backup before updating. Guest tracking links in e-mails already sent before this update contain no token: those customers see the masked status view until they receive a new e-mail (any status change sends one).

Database changes are applied automatically by Flyway on start-up (`backend/src/main/resources/db/migration`). Never edit a migration that already ran; add a new `V3__…sql` instead.

## 5. Backups (do this before going live)

```bash
# database → gzip file (run daily from cron)
docker compose -f docker-compose.prod.yml exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction void_store' | gzip > backup-$(date +%F).sql.gz

# product photos
docker run --rm -v void_uploads:/data -v "$PWD":/out alpine tar czf /out/uploads-$(date +%F).tgz -C /data .
```

Copy the backups off the server (for example to object storage). Check the volume name with `docker volume ls`.

## 6. After launch

- Submit `https://your-domain/sitemap.xml` in Google Search Console.
- Link previews on Instagram, WhatsApp and Facebook use each product's front photo, name and description — upload photos before sharing links.
- Health check: `https://your-domain/actuator/health`.

## Adding Paymob later

1. Create `PaymobPaymentProvider implements PaymentProvider` (method `PAYMOB`): create the payment intention and return its hosted-checkout URL from `start()`.
2. Add a webhook controller that verifies Paymob's HMAC and calls `OrderService.markPaid(number, transactionId)`.
3. Add the keys to `.env`, and add the method to the checkout page.
