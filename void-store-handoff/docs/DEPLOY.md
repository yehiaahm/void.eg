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

Fill in every value. Generate secrets with `openssl rand -base64 48`.

- `DOMAIN` is the bare domain (no `https://`).
- `MAIL_*` is any SMTP provider (Zoho Mail, Google Workspace, Brevo, Resend…). Without it the store still works, but no emails are sent.
- `OWNER_EMAIL` / `OWNER_PASSWORD` creates the first admin account on first start. After that, manage the team from Admin → Staff.

## 3. Start

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f app     # wait for "Started VoidApiApplication"
```

Caddy gets the HTTPS certificate automatically. Open `https://your-domain/admin` and sign in.

## 4. Update to a new version

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

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
