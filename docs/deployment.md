# Deploying Rez

---

# Deploying Rez — Standalone (lurch, CT 115)

## Current status (2026-03-25) — standalone projections blocked

Rez is deployed self-managed on lurch (CT 115, `https://maxdc.duckdns.org`).
The service is running and entities work (facility/court provisioning persists fine),
but **views and consumers never process any events** — `projection_offset` stays empty.

### What was investigated and ruled out

- **DB config** — `runtime-standalone.conf` already contains the correct
  `${?DB_HOST}` substitution. Bootstrap DIAG confirms:
  `r2dbc connection-factory host=postgres port=5432 database=rez user=rez` ✓
- **Code bug** — all integration tests pass, including `FacilityByBotTokenView` ✓
- **application.conf override** — redundant r2dbc block was added then reverted;
  the runtime-standalone.conf defaults are sufficient ✓
- **Serialization / TypeName** — events have correct `@TypeName` annotations ✓

### Suspected root cause

The standalone runtime ships a `ProjectionThrottlingController` that fires every
~50 seconds but never triggers journal queries. Strong hypothesis: projections are
**throttled/disabled at the license tier level** when running with a free/dev key
("Dev use only. Free keys at https://akka.io/key"). The system also crashes every
~15 minutes with "Akka terminated. Obtain free keys." which prevents projections
from ever catching up even if they did start.

### Next step

Email sent to Akka asking whether standalone projections require a paid key.
If confirmed: either get a paid standalone license or switch back to Akka Cloud.

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

Manual steps:

```shell
mvn install -DskipTests --settings settings.xml -Pgoogle
docker tag reservation:1.0 max8github/rez:1.0
docker push max8github/rez:1.0
akka service deploy rez max8github/rez:0.x --project rez-prod
```

If the service was soft-deleted (2-week restore window):

```shell
akka service restore rez --project rez-prod
akka service deploy rez max8github/rez:0.x --project rez-prod
```

## Secrets (set once, survive redeployments)

```shell
akka secret create generic telegram-secret --literal token=<BOT_TOKEN> --project rez-prod
akka secret create generic openai-secret --literal api-key=<KEY> --project rez-prod
akka secret create generic google-service-account --from-file credentials.json=<path> --project rez-prod
```

## Environment variables

```
OPENAI_API_KEY        (from openai-secret)
GOOGLE_APPLICATION_CREDENTIALS  (from google-service-account)
```

## Re-register Telegram webhook after each hostname change

```shell
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://<akka-hostname>/telegram/webhook"
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

## Provision facility and courts (one-time after a clean deploy)

```shell
HOST=https://<akka-hostname>
curl -s -X POST $HOST/facility/etc1en \
  -H "Content-Type: application/json" \
  -d '{"name":"Erster Tennisclub Edingen-Neckarhausen","address":{"street":"Mannheimer Str. 50","city":"68535 Edingen-Neckarhausen"}}'

for i in 1 2 3 4; do
  curl -s -X POST $HOST/facility/etc1en/resource/court-$i \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Court $i\"}"
done
```
