# Deploying VOID

The production stack is three containers on one server: **Caddy** (HTTPS) → **app** (Spring Boot serving the API, the storefront and the admin) → **MySQL**. Product photos live in a Docker volume. The live store runs on Railway instead — see [Railway](#railway) below.

### Where uploaded photos are stored

Photos uploaded in Admin → Products are written to `UPLOADS_DIR` (in the Docker image: `/data/uploads`, files under `products/`) and served at `/uploads/products/<hash>.<ext>`. A container's own disk is **wiped on every deploy**, so `UPLOADS_DIR` must be persistent storage: a Docker volume (`docker-compose.prod.yml` mounts `uploads` there) or a Railway volume. Without one, every uploaded photo returns 404 after the next deploy. Photos that ship with the code (`frontend/public/assets/products`, URLs starting `/assets/`) are not affected.

On start-up the app logs either `Product uploads are stored in /data/uploads` or a `WARN` saying the directory is not writable or, on Railway, that no volume is mounted there. The image starts as root only to give the volume to the app user (`deploy/docker-entrypoint.sh`), then runs the app as the unprivileged `void` user.

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

## WhatsApp order updates (optional, off by default)

The store can message customers on WhatsApp when their order is received, confirmed, shipped, delivered or cancelled. It uses Meta's **WhatsApp Business Cloud API**. Nothing is sent, and the checkout box is hidden, until all of this is done:

1. In Meta Business Manager: verify the business, add a WhatsApp Business number, and create a permanent access token (System User) with the `whatsapp_business_messaging` permission.
2. Create five **message templates** (category *Utility*), each in **both Arabic (`ar`) and English (`en`)**, named `void_order_placed`, `void_order_confirmed`, `void_order_shipped`, `void_order_delivered`, `void_order_cancelled` (the prefix can be changed with `WHATSAPP_TEMPLATE_PREFIX`). Each takes three body variables: `{{1}}` first name, `{{2}}` order number, `{{3}}` the customer's private tracking link. Example (shipped, English): *Hi {{1}}, your VOID order {{2}} is on its way. Track it: {{3}}*. Meta must approve each template before it can be sent; the wording is yours to write.
3. Set the variables on the app service and redeploy:

   | Variable | Value |
   |---|---|
   | `WHATSAPP_ENABLED` | `true` |
   | `WHATSAPP_TOKEN` | the permanent access token |
   | `WHATSAPP_PHONE_NUMBER_ID` | the WhatsApp number's *Phone number ID* (digits) |
   | `WHATSAPP_API_VERSION` | optional, default `v21.0` |
   | `WHATSAPP_TEMPLATE_PREFIX` | optional, default `void_order_` |

Customers are only messaged if they tick *Send me order updates on WhatsApp* at checkout (unticked by default; WhatsApp requires an explicit opt-in). The message goes to the mobile number on the order. Failures are only logged (`WhatsApp … failed`), never shown to the shopper. Mention WhatsApp messages in the privacy policy (Admin → Content) before switching this on.

## Railway

The live store (voideg.app) is the `Dockerfile` deployed by Railway from `main`, with Railway's MySQL (the `MYSQL*` variables are picked up automatically). Pushing to `main` redeploys it.

**Product photos need a volume.** Railway gives each deploy a fresh disk, so without a volume every photo uploaded in the admin disappears on the next deploy. One-time setup in the Railway dashboard:

1. Open the project and select the **app service** (the one built from this repo — not MySQL).
2. Add a volume to it: right-click the service (or use the **⌘K / Ctrl+K** command palette → *Create volume*) → **Attach volume** / **New volume**, and pick the app service.
3. Set the **mount path** to exactly `/data/uploads`.
4. In the service's **Variables**, check that `UPLOADS_DIR` is either not set or set to `/data/uploads`. If it is set to anything else, the mount path must be that value instead.
5. Railway redeploys the service. In the new deploy's logs, look for `Product uploads are stored in /data/uploads`. If you see `No Railway volume is mounted at …`, the mount path does not match.
6. Upload the missing photos again in Admin → Products. Photos uploaded before the volume existed are gone and cannot be recovered.

Notes: a service with a volume restarts with a few seconds of downtime on each deploy (Railway cannot overlap two copies that share a volume), and it cannot run more than one replica. Turn on volume backups in the volume's settings, or copy the photos off regularly. If photos ever need to live outside one server (several replicas, a CDN), replace `ImageStorage` with an S3 / Cloudflare R2 implementation.

## Adding Paymob later

1. Create `PaymobPaymentProvider implements PaymentProvider` (method `PAYMOB`): create the payment intention and return its hosted-checkout URL from `start()`.
2. Add a webhook controller that verifies Paymob's HMAC and calls `OrderService.markPaid(number, transactionId)`.
3. Add the keys to `.env`, and add the method to the checkout page.
