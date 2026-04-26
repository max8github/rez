# Deploying Rez — Akka Cloud

Rez runs on Akka Cloud (project `rez-prod`, region `europe-west1`).

For the standalone fallback on lurch see [deployment-standalone.md](deployment-standalone.md).

---

## Critical: build with `mvn install`, not a custom Dockerfile

The `akka-javasdk-parent` uses the fabric8 docker-maven-plugin to produce an image that
Akka Cloud's init container inspects to detect the SDK version. A custom `docker build`
produces the wrong image structure and causes a `CrashLoopBackOff`:

> "Could not detect the version of the sdk."

Always build via Maven.

---

## Container registry

Rez uses the **Akka Container Registry** (ACR) — no Docker Hub account or credentials needed.
The ACR path is `acr.europe-west1.akka.io/massimo-calderoni/rez-prod/reservation`.

The `akka service deploy --push` flag pushes the local image to ACR and deploys in one step.

---

## Build and deploy

```shell
./deploy.sh cloud               # build, push to ACR, deploy to Akka Cloud
./deploy.sh cloud --no-deploy   # build + push to ACR only (no deploy)
```

Manual steps (the version tag is printed in the build output as `Tagging image reservation:X.Y successful!`):

```shell
cd reservation
mvn install -DskipTests --settings settings.xml -Pgoogle
# image reservation:2.0 is now in local Docker daemon
akka service deploy rez reservation:2.0 --push --project rez-prod
```

Push only (without deploying):

```shell
akka container-registry push reservation:2.0 --project rez-prod
```

If the service was soft-deleted (2-week restore window):

```shell
akka service restore rez --project rez-prod
akka service deploy rez reservation:2.0 --push --project rez-prod
```

---

## Secrets (set once, survive redeployments)

```shell
akka secret create generic openai-secret --literal api-key=<KEY> --project rez-prod
```

> No `telegram-secret` is needed — the bot token is stored on the facility entity and
> routed dynamically via `FacilityByBotTokenView`.

---

## Environment variables

```
OPENAI_API_KEY           (from openai-secret)
REZ_CALENDAR_BASE_URL    optional — set to the public Akka service hostname
                         (e.g. https://small-frog-0557.europe-west1.akka.services)
                         to include Rez calendar links in booking notifications.
                         If omitted, calendar links are suppressed from notifications.
```

Set env vars at deploy time:

```shell
akka service deploy rez reservation:2.0 --push --project rez-prod \
  --secret-env OPENAI_API_KEY=openai-secret/api-key \
  --env REZ_CALENDAR_BASE_URL=https://small-frog-0557.europe-west1.akka.services
```

---

## Re-register Telegram webhook after each hostname change

```shell
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://<akka-hostname>/telegram/<TOKEN>/webhook"
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

---

## Provision facility and courts (one-time after a clean deploy)

Use the provisioning script — it handles facility creation, court registration, and Telegram webhook in one shot:

```shell
./scripts/provision-facility.sh \
  --host        https://<akka-hostname> \
  --name        "Erster Tennisclub Edingen-Neckarhausen" \
  --street      "Mannheimer Str. 50" \
  --city        "68535 Edingen-Neckarhausen" \
  --token       "<BOT_TOKEN>" \
  --courts      "Court 1:court-1,Court 2:court-2,Court 3:court-3,Court 4:court-4"
```

See [facility-provisioning-runbook.md](facility-provisioning-runbook.md) for the full step-by-step guide.
