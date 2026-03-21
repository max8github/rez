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

```shell
cd /Users/max/code/rez/reservation
mvn install -DskipTests --settings settings.xml -Pgoogle
docker tag com.rezhub.reservation/reservation:0.x max8github/rez:0.x
docker push max8github/rez:0.x
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
