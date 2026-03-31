# Rez Provisioning — Reference

This document covers one-time platform setup and keeps a record of provisioned facilities.

For the step-by-step runbook to onboard a new facility, see [facility-provisioning-runbook.md](facility-provisioning-runbook.md).

For deployment mechanics (build, push, deploy) see [deployment.md](deployment.md).

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

Akka uses persistent storage. **Provisioning is one-time** — entities survive restarts and
redeployments. Do not re-run provisioning unless you are explicitly resetting/recreating a facility.

### ID conventions

Callers never supply entity IDs. The API generates and returns bare UUIDs.
Keep the returned IDs — they are needed for subsequent resource provisioning.

---

## One-time infrastructure setup

### Secrets (standalone — lurch)

Secrets live in `/Users/max/code/mini-dc/env/prod/rez.env` and are mounted into the container.
Required:
- `OPENAI_API_KEY`
- `AKKA_LICENSE_KEY`
- Google credentials file at `secrets/credentials.json`

### Secrets (Akka Cloud)

Set once per project:

```shell
akka secret create generic openai-secret \
  --literal api-key=<OPENAI_KEY> --project rez-prod

akka secret create generic google-service-account \
  --from-file credentials.json=<path-to-credentials.json> --project rez-prod
```

> No `telegram-secret` needed — the bot token is stored on the facility entity and
> routed dynamically via `FacilityByBotTokenView`.

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

## ETC Edingen — current provisioned state (as of 2026-03-31, Akka Cloud)

| Item | Value |
|------|-------|
| Facility ID | `3e03bc92f67b47ceac75a59122c0cb78` |
| Name | Erster Tennisclub Edingen-Neckarhausen |
| Address | Mannheimer Str. 50, 68535 Edingen-Neckarhausen |
| Timezone | Europe/Berlin |
| Bot | @EtcEnBookingBot (`86752466...`) |
| Host | `https://small-frog-0557.europe-west1.akka.services` |
| Court 1 ID | `2c5180fb87ab45b397a3273e011f3041` |
| Court 2 ID | `0db28f44fb64460fbb85d2b959e4e858` |
| Court 3 ID | `6409c1a9ec5941a0b9daa923d9e90d1d` |
| Court 4 ID | `2810ce2d52c0408296deb01a169e59b8` |
| Google Calendar — court-1 | `3d228lvsdmdjmj79662t8r1fh4@group.calendar.google.com` |
| Google Calendar — court-2 | `63hd39cd9ppt8tajp76vglt394@group.calendar.google.com` |
| Google Calendar — court-3 | `42cf1e8db6c37f2a7c8f02dbf9b6fc9d497008ecd92a30892ea7b1a380c8e130@group.calendar.google.com` |
| Google Calendar — court-4 | `2bba1d7802c29ab3a4455cadaebc68b0bf79370ac009b053664c9a2decb2ea1a@group.calendar.google.com` |

---

## Eppelheimer Tennis-Club — current provisioned state (as of 2026-03-31, Akka Cloud)

| Item | Value |
|------|-------|
| Facility ID | `9495ef3bb207422c875c0cea13b7addd` |
| Name | Eppelheimer Tennis-Club e.V. |
| Address | Peter-Böhm-Straße 50, 69214 Eppelheim |
| Timezone | Europe/Berlin |
| Bot | @EtcBookBot (`84181241...`) |
| Host | `https://small-frog-0557.europe-west1.akka.services` |
| Court 1 ID | `5846a631faea4dc89eacb3bd34507e70` |
| Court 2 ID | `ba3ba76425254555883c0ece12806513` |
| Google Calendar — court-1 | `d5088961164845432bbe2f9a5e211cb4cc2461f4ef123840b2702e002166df7a@group.calendar.google.com` |
| Google Calendar — court-2 | `b4077bbda87f48d89f808a557ecae67c7535c5c2de7a89e52f28e3b416df2547@group.calendar.google.com` |
