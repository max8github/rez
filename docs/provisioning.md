# Rez Provisioning Guide

This document describes how to onboard a new facility (club) onto the Rez platform —
from creating the Telegram bot to provisioning courts and managing members.

For deployment mechanics (build, push, deploy to Akka Cloud) see [deployment.md](deployment.md).

---

## Concepts

### Admin roles

| Role | Who | Interface |
|------|-----|-----------|
| **Rez Admin** | Platform operator (Max) | `curl` / HTTP API |
| **Facility Admin** | Club operator | HTTP API (bot commands in future) |

**Rez Admin** creates facilities and sets who the Facility Admins are.
**Facility Admin** manages courts and club members for their own facility.

### One bot per facility (or per court group)

Each Rez facility maps 1:1 to a Telegram bot. If a club has distinct court groups with
different booking policies (e.g. outdoor members-only vs. indoor open), create a separate
facility for each:

| Rez facility | Courts | Bot | Policy |
|---|---|---|---|
| `etc1en-outdoor` | Clay courts 1–4 | @ETCOutdoorBot | `MEMBERS_ONLY` |
| `etc1en-halle` | Indoor courts | @ETCHalleBot | `OPEN` |

### Persistent state

Akka Cloud uses persistent storage. **Provisioning is one-time** — entities survive
restarts and redeployments. Do not re-run provisioning unless you are explicitly
resetting/recreating a facility.

### ID conventions (current)

Caller supplies plain names (e.g. `etc1en`, `court-1`). Internally:
- Facility entity ID: `f_{name}` (e.g. `f_etc1en`)
- Resource entity ID: `r_{resourceId}` (e.g. `r_court-1`)

> **Note:** ID generation will be internalized in a future release (backlog #3).
> After that, endpoints will generate and return IDs — callers will not supply them.

---

## Prerequisites (one-time per club)

### 1. Create Telegram bot

1. Open Telegram, start a chat with `@BotFather`
2. Send `/newbot` and follow the prompts — provide a name and username
3. Save the **bot token** (format: `123456789:ABCdef...`)
4. Optionally set bot commands via `/setcommands`

### 2. Create Google Calendars

Create one Google Calendar per court in the club's Google account:

1. Go to Google Calendar → Settings → Add calendar → Create new calendar
2. Name it clearly (e.g. "ETC Court 1")
3. Share it with the Rez service account email (from `credentials.json`) with
   "Make changes to events" permission
4. Note the **Calendar ID** from Settings → Integrate calendar
   (format: `abc123@group.calendar.google.com`)

> **Note:** Calendar IDs are currently stored in `application.conf` and require a rebuild
> to add a new court. After backlog #7 is implemented, calendar IDs will be stored on
> the resource entity and passed in via the provisioning API — no rebuild needed.

### 3. Configure application.conf (current approach — pre backlog #7)

Add entries to `reservation/reservation/src/main/resources/application.conf`:

```
google.resource-calendars {
  "court-1" = "<calendar-id>"
  "court-2" = "<calendar-id>"
  "court-3" = "<calendar-id>"
  "court-4" = "<calendar-id>"
}
```

Then rebuild and redeploy (see [deployment.md](deployment.md)).

### 4. Set Akka Cloud secrets (once per project — already done for rez-prod)

```shell
akka secret create generic telegram-secret \
  --literal token=<BOT_TOKEN> --project rez-prod

akka secret create generic openai-secret \
  --literal api-key=<OPENAI_KEY> --project rez-prod

akka secret create generic google-service-account \
  --from-file credentials.json=<path-to-credentials.json> --project rez-prod
```

---

## Provision a new facility (Rez Admin)

### Current approach (pre bot-token-routing)

Set environment variable before deploying:
```
FACILITY_ID=etc1en
```

Then provision the facility entity:

```shell
HOST=https://damp-mud-7270.gcp-us-east1.akka.services

curl -s -X POST $HOST/facility/etc1en \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Erster Tennisclub Edingen-Neckarhausen",
    "address": {
      "street": "Mannheimer Str. 50",
      "city": "68535 Edingen-Neckarhausen"
    }
  }'
```

### Target approach (after bot-token-routing backlog item)

No `FACILITY_ID` env var. Pass the bot token and Facility Admin user IDs at creation time:

```shell
curl -s -X POST $HOST/facility/etc1en \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Erster Tennisclub Edingen-Neckarhausen",
    "address": {
      "street": "Mannheimer Str. 50",
      "city": "68535 Edingen-Neckarhausen"
    },
    "botToken": "123456789:ABCdef...",
    "adminUserIds": ["987654321"]
  }'
```

The deployment automatically routes incoming messages to the correct facility by bot token.

---

## Provision courts (Facility Admin or Rez Admin)

### Current approach (pre backlog #7)

Calendar IDs are in `application.conf` (see Prerequisites above). Court entities are created
without a calendarId parameter:

```shell
HOST=https://damp-mud-7270.gcp-us-east1.akka.services
FACILITY=etc1en

for i in 1 2 3 4; do
  curl -s -X POST $HOST/facility/$FACILITY/resource/court-$i \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"Court $i\"}"
done
```

### Target approach (after backlog #7)

Pass `calendarId` directly — no config change, no redeploy needed:

```shell
curl -s -X POST $HOST/facility/$FACILITY/resource/court-1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Court 1", "calendarId": "abc123@group.calendar.google.com"}'
```

---

## Register the Telegram webhook

Must be run once after initial deployment, and again after any hostname change:

```shell
TOKEN=<bot-token>
HOST=damp-mud-7270.gcp-us-east1.akka.services

# Current (pre bot-token-routing)
curl "https://api.telegram.org/bot$TOKEN/setWebhook?url=https://$HOST/telegram/webhook"

# Target (after bot-token-routing)
curl "https://api.telegram.org/bot$TOKEN/setWebhook?url=https://$HOST/telegram/$TOKEN/webhook"

# Verify
curl "https://api.telegram.org/bot$TOKEN/getWebhookInfo"
```

---

## Verify provisioning

```shell
# Check facility entity
curl -s $HOST/facility/etc1en

# Check a resource entity (note the r_ prefix in the URL)
curl -s $HOST/resource/r_court-1
```

Expected: facility response includes `resourceIds: ["r_court-1", "r_court-2", ...]`

---

## Manage members (after member provisioning sprint)

> This section describes the **target design** — not yet implemented.

### Booking policy

Each facility has a `BookingPolicy`: `OPEN` (anyone can book) or `MEMBERS_ONLY`.
Toggle via API:

```shell
curl -s -X PUT $HOST/facility/etc1en/policy \
  -H "Content-Type: application/json" \
  -d '{"policy": "MEMBERS_ONLY"}'
```

### Add a member directly (Facility Admin)

```shell
curl -s -X POST $HOST/facility/etc1en/members/987654321
```

### Member self-registration flow

1. User DMs the bot — `UserEntity` is auto-created on first contact
2. User tries to book a `MEMBERS_ONLY` facility — agent prompts them to request membership
3. User confirms — Facility Admin receives a bot notification
4. Facility Admin approves: `/approve 987654321`
5. User is notified and can now book

---

## Local smoke run

Run with stub calendar and notifier (no external API calls):

```shell
cd /Users/max/code/rez/reservation/reservation
mvn exec:java -Plocal
```

In a second terminal, provision and test:

```shell
# Provision
curl -s -X POST http://localhost:9000/facility/test \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Club","address":{"street":"Test St 1","city":"12345 Test"}}'

curl -s -X POST http://localhost:9000/facility/test/resource/court-1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Court 1"}'

# Send a booking (requires OPENAI_API_KEY in env)
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

# Verify the booking was stored
curl -s http://localhost:9000/resource/r_court-1
```

> The local smoke run uses the real OpenAI API. After backlog #13 (test infra) is done,
> `BookingAgentIntegrationTest` will cover the same flow without requiring an API key.

---

## ETC Edingen — current provisioned state (as of 2026-03-19)

| Item | Value |
|------|-------|
| Facility ID | `etc1en` |
| Name | Erster Tennisclub Edingen-Neckarhausen |
| Address | Mannheimer Str. 50, 68535 Edingen-Neckarhausen |
| Courts | court-1, court-2, court-3, court-4 |
| Akka Cloud host | `damp-mud-7270.gcp-us-east1.akka.services` |
| Google Calendar — court-1 | `3d228lvsdmdjmj79662t8r1fh4@group.calendar.google.com` |
| Google Calendar — court-2 | `63hd39cd9ppt8tajp76vglt394@group.calendar.google.com` |
| Google Calendar — court-3 | `42cf1e8db6c37f2a7c8f02dbf9b6fc9d497008ecd92a30892ea7b1a380c8e130@group.calendar.google.com` |
| Google Calendar — court-4 | `2bba1d7802c29ab3a4455cadaebc68b0bf79370ac009b053664c9a2decb2ea1a@group.calendar.google.com` |
