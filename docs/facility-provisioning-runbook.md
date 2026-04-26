# Facility Provisioning Runbook

Step-by-step guide for onboarding a new facility (club) onto Rez.

**Assumes:** secrets and infrastructure are already in place — see [provisioning.md](provisioning.md) for the one-time setup.

---

## Step 1 — Create a Telegram bot

1. Open Telegram, start a chat with `@BotFather`
2. Send `/newbot` and follow the prompts (provide a name and a username, e.g. `@EtcEnBookingBot`)
3. Save the **bot token** (format: `123456789:ABCdef...`)
4. Optionally set a description and commands:
   ```
   /setdescription → "Book a court at <Facility Name>"
   /setcommands → (leave empty — Rez uses free-form natural language)
   ```

One bot per facility. If a club has distinct court groups with different policies, create a separate bot (and a separate Rez facility) for each group.

---

## Step 2 — Google Calendars (optional, future use)

Google Calendar integration is currently disabled. The `calendarId` field stored on each court resource is reserved for future re-activation.

The provisioning script can create Google Calendars automatically if you pass `--credentials` with a service account file. If you skip `--credentials`, pass court names as `"Name:placeholder-id"` pairs so the script does not attempt calendar creation:

```
--courts "Court 1:court-1,Court 2:court-2,Court 3:court-3,Court 4:court-4"
```

---

## Step 3 — Run the provisioning script

The script creates the facility entity, registers each court, and registers the Telegram webhook in one shot.

```shell
./scripts/provision-facility.sh \
  --host        https://<akka-hostname> \
  --name        "Erster Tennisclub Edingen-Neckarhausen" \
  --street      "Mannheimer Str. 50" \
  --city        "68535 Edingen-Neckarhausen" \
  --token       "123456789:ABCdef..." \
  --admins      "987654321" \
  --courts      "Court 1:court-1,Court 2:court-2,Court 3:court-3,Court 4:court-4"
```

`--host`, `--webhook-host`, and `--timezone` all have sensible defaults and can be omitted for the standard deployment.

**Arguments:**

| Argument | Default | Description |
|---|---|---|
| `--host` | `https://rez.rezbotapp.com` | Base URL for Rez API calls |
| `--webhook-host` | same as `--host` | Public URL Telegram sends webhooks to |
| `--name` | required | Full facility name |
| `--street` | required | Street address |
| `--city` | required | City and postal code |
| `--timezone` | `Europe/Berlin` | IANA timezone |
| `--token` | required | Telegram bot token |
| `--admins` | — | Comma-separated Telegram user IDs for facility admins |
| `--credentials` | `$GOOGLE_CREDENTIALS_FILE` or `./credentials.json` | Path to Google service account credentials JSON — only needed if creating Google Calendars |
| `--courts` | required | Comma-separated court names. Use `"Name:calendarId"` to supply a fixed ID (and skip Google Calendar creation). |

The script prints a summary with all generated IDs at the end.

---

## Step 4 — Record the generated IDs

Copy the output into the **"Current provisioned state"** table at the bottom of [provisioning.md](provisioning.md).
You will need the facility ID if you ever need to re-provision a court or update facility metadata.

---

## Step 5 — Verify

```shell
HOST=https://<akka-hostname>
FACILITY_ID=<id from script output>
BOT_TOKEN=<bot token>

# Check facility entity — should show name, botToken, timezone (no resourceIds — those live on resources)
curl -s $HOST/facility/$FACILITY_ID | python3 -m json.tool

# Check that courts are associated with the facility
curl -s "$HOST/resource/by-facility/$FACILITY_ID" | python3 -m json.tool

# Check webhook registration
curl -s "https://api.telegram.org/bot$BOT_TOKEN/getWebhookInfo" | python3 -m json.tool
```

Expected webhook response:
```json
{
  "url": "https://<akka-hostname>/telegram/<token>/webhook",
  "has_custom_certificate": false,
  "pending_update_count": 0
}
```

---

## Step 6 — Smoke test

Send a message to the bot from Telegram:

> "What courts are available tomorrow at 6pm?"

The bot should reply within a few seconds. If it does not:
- Check the Akka Cloud console for errors (`akka service logs rez --project rez-prod`)
- Look for `"No facility found for bot token"` — this means the `FacilityByBotTokenView` has not populated yet (projection lag)
- Wait ~30 seconds and retry; projections catch up from the journal on startup

---

## Webhook re-registration

Re-run whenever the hostname changes (e.g. after a DNS update or new deployment target):

```shell
BOT_TOKEN=<token>
HOST=<akka-hostname>

curl "https://api.telegram.org/bot$BOT_TOKEN/setWebhook?url=https://$HOST/telegram/$BOT_TOKEN/webhook"
```
