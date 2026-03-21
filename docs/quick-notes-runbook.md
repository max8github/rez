This is my quick file before the demo that keeps commands to copy/paste.

# CLOUD DEPLOYMENT (Akka Cloud — correct approach)

**Always build with `mvn install`, never a custom Dockerfile.**
The `akka-javasdk-parent` uses JIB to produce an image that Akka Cloud's init
container can inspect to detect the SDK version. A custom `docker build` produces
the wrong image structure and causes a `CrashLoopBackOff` with
"Could not detect the version of the sdk."

**`application.conf` must NOT contain on-prem settings.** Remove:
- `akka.actor.provider = cluster`  → the platform sets this
- `akka.persistence.r2dbc { ... }` → the platform manages persistence

**Build and push image:**
```shell
cd /Users/max/code/rez/reservation
mvn install -DskipTests --settings settings.xml -Pgoogle
docker tag com.rezhub.reservation/reservation:0.x max8github/rez:0.x
docker push max8github/rez:0.x
```

**Deploy / redeploy:**
```shell
akka service deploy rez max8github/rez:0.x --project rez-prod
```

**If service was soft-deleted** (2-week restore window):
```shell
akka service restore rez --project rez-prod
akka service deploy rez max8github/rez:0.x --project rez-prod
```

**Secrets (set once, survive redeployments):**
```shell
akka secret create generic telegram-secret --literal token=<BOT_TOKEN> --project rez-prod
akka secret create generic openai-secret --literal api-key=<KEY> --project rez-prod
# Google service account key:
akka secret create generic google-service-account --from-file credentials.json=<path> --project rez-prod
```

**Environment variables for the service:**
```
FACILITY_ID=etc1en
OPENAI_API_KEY (from openai-secret)
TELEGRAM_BOT_TOKEN (from telegram-secret)
GOOGLE_APPLICATION_CREDENTIALS (from google-service-account)
```

**Re-register Telegram webhook after each hostname change:**
```shell
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://<akka-hostname>/telegram/webhook"
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

**Provision facility and courts (one-time after a clean deploy):**
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

# CLI - LOCAL

Provision facility and courts for local test

```shell
cd /Users/max/code/rez/reservation/reservation
mvn exec:java -Plocal
```

```shell
curl -s -X POST http://localhost:9000/facility/test \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Club","address":{"street":"Test St 1","city":"12345 Test"}}'

curl -s -X POST http://localhost:9000/facility/test/resource/court-1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Court 1"}'

curl -s -X POST http://localhost:9000/facility/test/resource/court-2 \
  -H "Content-Type: application/json" \
  -d '{"name":"Court 2"}'
```

Then use f_test as the facility_id in the agent messages (that's what gets stored internally).

Book with Matrix:
```shell
curl -s -X POST http://localhost:9000/matrix/message \
  -H "Content-Type: application/json" \
  -d '{"facility_id":"f_test","sender":"@max:local","sender_name":"Max","message":"Book a court tomorrow at 10am for Max and Anna"}'
```

Check the court was booked:
```shell
curl -s http://localhost:9000/facility/test

curl -s http://localhost:9000/resource/r_court-1


curl -s http://localhost:9000/facility/f_test/reservations

curl -s http://localhost:9000/facility/f_test/resource/r_court-1/reservations

```

Book with Telegram:
```shell
curl -s -X POST http://localhost:9000/telegram/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "message_id": 1,
      "from": {"id": 123456, "first_name": "Max", "username": "max"},
      "chat": {"id": 123456, "type": "private"},
      "text": "Book a court tomorrow at 10am for Max and Anna"
    }
  }'
```