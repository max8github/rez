# Deploying Rez

---

# Deploying Rez — Standalone (lurch, CT 115) — fallback only

**Primary deployment is Akka Cloud.** Standalone on lurch is a fallback option.

## Standalone projection status

Projections were previously blocked in standalone mode with a free/dev license key.
The root cause was suspected to be a license-tier limitation — free keys caused 15-minute
crashes before projections could catch up. This was not retested after switching to Akka Cloud,
so standalone is considered **untested** for the current codebase.

If activating standalone: obtain a paid Akka standalone license from account.akka.io.

---

## Deployment layout

```
deploy/standalone/compose.yaml      # Docker Compose stack (synced to lurch on deploy)
deploy/standalone/rez.env.template  # Secret variable template — copy to .env and fill in
deploy/standalone/.env              # Actual secrets (gitignored, lives on lurch at /home/rez/.env)
```

## Build and deploy

```shell
./deploy.sh standalone          # build, push to Gitea, sync compose.yaml, redeploy on lurch
./deploy.sh standalone --no-deploy  # build + push only
```

## First-time setup on lurch (after CT 115 is provisioned)

```shell
# 1. Secrets file
cp deploy/standalone/rez.env.template deploy/standalone/.env
# edit .env with real values, then push to lurch:
scp deploy/standalone/.env lurch:/tmp/rez.env
ssh lurch "pct push 115 /tmp/rez.env /home/rez/.env && rm /tmp/rez.env"

# 2. Google service account credentials
scp /path/to/credentials.json lurch:/tmp/credentials.json
ssh lurch "pct exec 115 -- mkdir -p /home/rez/secrets && \
           pct push 115 /tmp/credentials.json /home/rez/secrets/credentials.json && \
           rm /tmp/credentials.json"

# 3. Register Telegram webhook (after cloudflared is running — see below)
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://rez.rezbotapp.com/telegram/<TOKEN>/webhook"
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

## Cloudflare Tunnel (CT 115)

Telegram is effectively IPv4-only and the home connection is DS-Lite CGNAT (no IPv4
inbound). A Cloudflare Tunnel runs outbound from CT 115, bypassing the NAT entirely.

```
Telegram → Cloudflare edge (IPv4/HTTPS) → cloudflared (CT 115) → localhost:9000 → Rez
```

**Current tunnel state:**
- Domain: `rez.rezbotapp.com` (registered via Cloudflare Registrar)
- Tunnel name: `rez`, ID: `70d2e39f-6cdb-44fc-9462-5c2a99e1ef11`
- DNS: `rez.rezbotapp.com` CNAME → tunnel (managed by Cloudflare)
- Service: `cloudflared.service` enabled and running in CT 115
- Webhook: `https://rez.rezbotapp.com/telegram/{TOKEN}/webhook` ✓

**Credentials** (backed up to Mac):
- `/root/.cloudflared/70d2e39f-6cdb-44fc-9462-5c2a99e1ef11.json` (tunnel credentials)
- `/root/.cloudflared/cert.pem` (origin cert)

**If CT 115 needs to be rebuilt**, reinstall cloudflared, restore the two credential
files, write `/root/.cloudflared/config.yml`:
```yaml
tunnel: 70d2e39f-6cdb-44fc-9462-5c2a99e1ef11
credentials-file: /root/.cloudflared/70d2e39f-6cdb-44fc-9462-5c2a99e1ef11.json
ingress:
  - service: http://localhost:9000
```
Then `cloudflared service install && systemctl start cloudflared`.

## Known quirk: single-node mode

`STANDALONE_SINGLE_NODE=true` env var is silently ignored by runtime 1.5.35.
Single-node mode must be set via JVM system property instead — already done in
`compose.yaml`:
```
JAVA_TOOL_OPTIONS=-Dakka.runtime.standalone.single-node=true
```

---

# Deploying Rez — Akka Cloud

## Critical: build with `mvn install`, not a custom Dockerfile

The `akka-javasdk-parent` uses JIB to produce an image that Akka Cloud's init
container inspects to detect the SDK version. A custom `docker build` produces
the wrong image structure and causes a `CrashLoopBackOff`:

> "Could not detect the version of the sdk."

## `application.conf` must NOT contain on-prem settings

Remove these blocks before deploying to the cloud — the platform manages them:

- `akka.actor.provider = cluster`
- `akka.persistence.r2dbc { ... }`

## Build and deploy

```shell
./deploy.sh cloud               # build, push to Docker Hub, deploy to Akka Cloud
./deploy.sh cloud --no-deploy   # build + push only
```

Manual steps (version is the `reservation` module version in `reservation/pom.xml`):

```shell
mvn install -DskipTests --settings settings.xml -Pgoogle
docker tag reservation:2.0 max8github/rez:2.0
docker push max8github/rez:2.0
akka service deploy rez max8github/rez:2.0 --project rez-prod
```

If the service was soft-deleted (2-week restore window):

```shell
akka service restore rez --project rez-prod
akka service deploy rez max8github/rez:2.0 --project rez-prod
```

## Secrets (set once, survive redeployments)

```shell
akka secret create generic telegram-secret --literal token=<BOT_TOKEN> --project rez-prod
akka secret create generic openai-secret --literal api-key=<KEY> --project rez-prod
akka secret create generic google-service-account --from-file credentials.json=<path> --project rez-prod
```

## Environment variables

```
OPENAI_API_KEY           (from openai-secret)
REZ_CALENDAR_BASE_URL    optional — set to the public Akka service hostname
                         (e.g. https://small-frog-0557.europe-west1.akka.services)
                         to include Rez calendar links in booking notifications.
                         If omitted, calendar links are suppressed from notifications.
```

## Re-register Telegram webhook after each hostname change

```shell
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://<akka-hostname>/telegram/webhook"
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

## Provision facility and courts (one-time after a clean deploy)

Use the provisioning script — it handles facility creation, court registration, and Telegram webhook in one shot:

```shell
./scripts/provision-facility.sh \
  --host        https://<akka-hostname> \
  --name        "Erster Tennisclub Edingen-Neckarhausen" \
  --street      "Mannheimer Str. 50" \
  --city        "68535 Edingen-Neckarhausen" \
  --token       "<BOT_TOKEN>" \
  --courts      "Court 1,Court 2,Court 3,Court 4"
```

See [facility-provisioning-runbook.md](facility-provisioning-runbook.md) for the full step-by-step guide.
