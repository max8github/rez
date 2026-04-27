# Deploying Rez — Standalone (lurch, CT 115)

This is the **fallback** deployment option. Primary deployment is Akka Cloud — see [deployment.md](deployment.md).

---

## Status

Standalone was the original production target but is no longer actively used.
It was last tested with an earlier codebase version. Projections were previously blocked under a free/dev license key (see below). Not retested since switching to Akka Cloud.

**If activating standalone:** obtain a paid Akka standalone license from account.akka.io.

---

## Projection issue (historical)

Projections were blocked in standalone mode with a free/dev license key. Symptoms:
- `projection_offset` table stays empty
- Views and consumers never process events
- Service crashes every ~15 minutes with "Akka terminated. Obtain free keys."

Suspected cause: `ProjectionThrottlingController` is disabled at the free license tier.
The issue was never conclusively confirmed — we switched to Akka Cloud instead.

---

## File layout

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

The build script extracts the image tag from the Maven build output (`Tagging image reservation:X.Y successful!`) and retags it for the Gitea registry.

---

## First-time setup on lurch

```shell
# 1. Secrets file
cp deploy/standalone/rez.env.template deploy/standalone/.env
# edit .env with real values, then push to lurch:
scp deploy/standalone/.env lurch:/tmp/rez.env
ssh lurch "pct push 115 /tmp/rez.env /home/rez/.env && rm /tmp/rez.env"

# 3. Register Telegram webhook (after cloudflared is running — see below)
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://rez.rezbotapp.com/telegram/<TOKEN>/webhook"
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

---

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

---

## Known quirk: single-node mode

`STANDALONE_SINGLE_NODE=true` env var is silently ignored by runtime 1.5.35.
Single-node mode must be set via JVM system property instead — already done in
`compose.yaml`:
```
JAVA_TOOL_OPTIONS=-Dakka.runtime.standalone.single-node=true
```

---

## Provision facility and courts

Same provisioning script as Akka Cloud — pass the standalone host:

```shell
./scripts/provision-facility.sh \
  --host        https://rez.rezbotapp.com \
  --name        "Erster Tennisclub Edingen-Neckarhausen" \
  --street      "Mannheimer Str. 50" \
  --city        "68535 Edingen-Neckarhausen" \
  --token       "<BOT_TOKEN>" \
  --courts      "Court 1:court-1,Court 2:court-2,Court 3:court-3,Court 4:court-4"
```
