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

### ID conventions

Callers never supply entity IDs. The API generates and returns bare UUIDs.
Keep the returned IDs — they are needed for subsequent resource provisioning.

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

### 3. Ensure secrets are in place

**Standalone (lurch):** secrets are in `/Users/max/code/mini-dc/env/prod/rez.env`
and mounted into the container. `OPENAI_API_KEY` and the Google credentials file
(`secrets/credentials.json`) must be present.

**Akka Cloud:** set once per project:

```shell
akka secret create generic openai-secret \
  --literal api-key=<OPENAI_KEY> --project rez-prod

akka secret create generic google-service-account \
  --from-file credentials.json=<path-to-credentials.json> --project rez-prod
```

> No `telegram-secret` needed — the bot token is stored on the facility entity and
> routed dynamically via `FacilityByBotTokenView`.

---

## Provision a new facility (Rez Admin)

```shell
HOST=https://maxdc.duckdns.org          # standalone (lurch)
# HOST=https://<akka-cloud-hostname>    # Akka Cloud

FACILITY_ID=$(curl -s -X POST $HOST/facility \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Erster Tennisclub Edingen-Neckarhausen",
    "address": {
      "street": "Mannheimer Str. 50",
      "city": "68535 Edingen-Neckarhausen"
    },
    "timezone": "Europe/Berlin",
    "botToken": "123456789:ABCdef...",
    "adminUserIds": ["987654321"]
  }')
echo "Facility ID: $FACILITY_ID"
```

The returned `FACILITY_ID` is the bare UUID. Use it for all subsequent commands.

---

## Provision courts (Facility Admin or Rez Admin)

Pass `calendarId` directly — no config change, no redeploy needed:

```shell
COURT1=$(curl -s -X POST $HOST/facility/$FACILITY_ID/resource \
  -H "Content-Type: application/json" \
  -d '{"name": "Court 1", "calendarId": "abc123@group.calendar.google.com"}')

COURT2=$(curl -s -X POST $HOST/facility/$FACILITY_ID/resource \
  -H "Content-Type: application/json" \
  -d '{"name": "Court 2", "calendarId": "def456@group.calendar.google.com"}')

# ...repeat for additional courts
```

---

## Register the Telegram webhook

Must be run once after initial deployment, and again after any hostname change:

```shell
TOKEN=<bot-token>
HOST=maxdc.duckdns.org

curl "https://api.telegram.org/bot$TOKEN/setWebhook?url=https://$HOST/telegram/$TOKEN/webhook"

# Verify
curl "https://api.telegram.org/bot$TOKEN/getWebhookInfo"
```

---

## Verify provisioning

```shell
# Check facility entity (use the UUID returned at creation time)
curl -s $HOST/facility/$FACILITY_ID

# Check a resource entity (use the UUID returned at creation time, r_ prefix added internally)
curl -s $HOST/resource/$COURT1
```

Expected: facility response includes `resourceIds` with the registered court IDs,
`botToken`, `timezone`, and `adminUserIds`.

---

## Manage members (after member provisioning sprint)

> This section describes the **target design** — not yet implemented.

### Booking policy

Each facility has a `BookingPolicy`: `OPEN` (anyone can book) or `MEMBERS_ONLY`.
Toggle via API:

```shell
curl -s -X PUT $HOST/facility/$FACILITY_ID/policy \
  -H "Content-Type: application/json" \
  -d '{"policy": "MEMBERS_ONLY"}'
```

### Add a member directly (Facility Admin)

```shell
curl -s -X POST $HOST/facility/$FACILITY_ID/members/987654321
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

See [quick-notes-runbook.md](quick-notes-runbook.md) for copy-paste curl commands.

---

## ETC Edingen — current provisioned state (as of 2026-03-25)

Provisioned on standalone (lurch). **Note:** views/consumers not yet processing
events — see [deployment.md](deployment.md) for the current blocker.

| Item | Value |
|------|-------|
| Facility ID | `5fd6338d3a034593b4d368e0155b2d5e` |
| Name | Erster Tennisclub Edingen-Neckarhausen |
| Address | Mannheimer Str. 50, 68535 Edingen-Neckarhausen |
| Timezone | Europe/Berlin |
| Host | `https://maxdc.duckdns.org` |
| Google Calendar — court-1 | `3d228lvsdmdjmj79662t8r1fh4@group.calendar.google.com` |
| Google Calendar — court-2 | `63hd39cd9ppt8tajp76vglt394@group.calendar.google.com` |
| Google Calendar — court-3 | `42cf1e8db6c37f2a7c8f02dbf9b6fc9d497008ecd92a30892ea7b1a380c8e130@group.calendar.google.com` |
| Google Calendar — court-4 | `2bba1d7802c29ab3a4455cadaebc68b0bf79370ac009b053664c9a2decb2ea1a@group.calendar.google.com` |
