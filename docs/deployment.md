# Deploying Rez

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

# Deploying Rez to Akka Cloud

## Critical: build with `mvn install`, not a custom Dockerfile

The `akka-javasdk-parent` uses JIB to produce an image that Akka Cloud's init
container inspects to detect the SDK version. A custom `docker build` produces
the wrong image structure and causes a `CrashLoopBackOff`:

> "Could not detect the version of the sdk."

## `application.conf` must NOT contain on-prem settings

Remove these blocks before deploying to the cloud — the platform manages them:

- `akka.actor.provider = cluster`
- `akka.persistence.r2dbc { ... }`

## Build and push image

Use the build script — it handles both standalone (lurch) and cloud targets:

```shell
cd /Users/max/code/rez/reservation
./build-push.sh standalone          # build, push to Gitea, redeploy on lurch
./build-push.sh cloud               # build, push to Docker Hub, deploy to Akka Cloud
./build-push.sh standalone --no-deploy  # build + push only
```

Manual steps (cloud):

```shell
mvn install -DskipTests --settings settings.xml -Pgoogle
docker tag reservation:1.0 max8github/rez:1.0
docker push max8github/rez:1.0
```

## Deploy / redeploy

```shell
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
FACILITY_ID=etc1en
OPENAI_API_KEY        (from openai-secret)
TELEGRAM_BOT_TOKEN    (from telegram-secret)
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
