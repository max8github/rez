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
akka secret create generic openai --literal key=<KEY> --project rez-prod
```

> No `telegram-secret` is needed — the bot token is stored on the facility entity and
> routed dynamically via `FacilityByBotTokenView`.

---

## Environment variables

```
OPENAI_API_KEY           (from openai)
REZ_CALENDAR_BASE_URL    optional — set to the public Akka service hostname
                         (e.g. https://small-frog-0557.europe-west1.akka.services)
                         to include Rez calendar links in booking notifications.
                         If omitted, calendar links are suppressed from notifications.
```

`REZ_CALENDAR_BASE_URL` must point to the public Rez hostname itself, not to Hit and not to a
temporary local tunnel URL. Telegram booking confirmations build calendar links from this value.
If you accidentally deploy Rez with a stale `trycloudflare.com` URL, booking will still work but
Telegram users will receive broken "Open calendar" links.

Set env vars at deploy time:

```shell
akka service deploy rez reservation:2.0 --push --project rez-prod \
  --secret-env OPENAI_API_KEY=openai/key \
  --env REZ_CALENDAR_BASE_URL=https://small-frog-0557.europe-west1.akka.services
```

### Verify deployed env after every cloud deploy

After deploy, verify both the service route and the configured env:

```shell
akka service get rez --project rez-prod
```

Confirm that:

1. `Routes` shows the public Rez hostname
2. `REZ_CALENDAR_BASE_URL` is exactly that same hostname

For example, if the route is:

```text
red-shadow-4568.europe-west1.akka.services
```

then the env must be:

```text
REZ_CALENDAR_BASE_URL=https://red-shadow-4568.europe-west1.akka.services
```

### Fresh deploy catch: the hostname is only known after the first deploy

On a completely fresh Akka Cloud deployment, there is a bootstrapping catch:

1. Before the first deploy, you do not yet know the final public hostname.
2. `REZ_CALENDAR_BASE_URL` should point to that final public hostname.

So a clean deployment is effectively a two-step process:

1. First deploy Rez without `REZ_CALENDAR_BASE_URL`, or with a temporary placeholder if needed.
   Result: Rez starts, but calendar links in Telegram notifications are omitted or not yet correct.
2. Read the assigned hostname from:
   ```shell
   akka service get rez --project rez-prod
   ```
3. Deploy again with:
   ```shell
   --env REZ_CALENDAR_BASE_URL=https://<actual-rez-hostname>
   ```
4. Verify with `akka service get rez --project rez-prod`.
5. Send a Telegram test booking and open the returned calendar link in a browser.

In other words: for a true from-scratch deployment, yes, you should expect to deploy twice.

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
  --courts      "Court 1,Court 2,Court 3,Court 4"
```

This default path stores an empty `calendarId` on each court. If you want to preserve an existing calendar identifier as metadata, use `"Name:calendarId"` pairs instead.

See [facility-provisioning-runbook.md](facility-provisioning-runbook.md) for the full step-by-step guide.
