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

## Step 2 — Create Google Calendars

Create **one Google Calendar per court** in the club's Google account:

1. Go to [Google Calendar](https://calendar.google.com) → Settings → **Add calendar → Create new calendar**
2. Name it clearly, e.g. "ETC Court 1"
3. Share it with the Rez service account email (found in `credentials.json`) with **"Make changes to events"** permission
4. Open **Settings → Integrate calendar** and copy the **Calendar ID**
   (format: `abc123@group.calendar.google.com`)

Repeat for every court. Note all calendar IDs before continuing.

> **Note — calendar ownership:** When creating calendars manually via the UI, your personal Google account is the owner and the service account is a shared editor. The older ETC calendars (`Tennis Court #1` etc.) have the service account (`kalix-rez@rezcal.iam.gserviceaccount.com`) as **owner** because they were originally created via the Calendar API authenticated as the service account.
>
> **Future improvement:** automate Step 2 by calling `POST https://www.googleapis.com/calendar/v3/calendars` with the service account credentials — the script can create, name, and return calendar IDs without touching the Google Calendar UI. This would make the provisioning script fully automated.
>
> **Fixing existing calendars:** if you want the service account to own a manually-created calendar, use **Settings → Sharing → Transfer ownership** (the option is visible in the sharing dropdown). After transfer, the service account becomes owner and you retain "Make changes and manage sharing".

---

## Step 3 — Run the provisioning script

The script creates the facility entity, registers each court, and registers the Telegram webhook in one shot.

```shell
./scripts/provision-facility.sh \
  --name    "Erster Tennisclub Edingen-Neckarhausen" \
  --street  "Mannheimer Str. 50" \
  --city    "68535 Edingen-Neckarhausen" \
  --token   "123456789:ABCdef..." \
  --admins  "987654321" \
  --courts  "Court 1:abc123@group.calendar.google.com,Court 2:def456@group.calendar.google.com"
```

`--host`, `--webhook-host`, and `--timezone` all have sensible defaults and can be omitted for the standard deployment.

**Arguments:**

| Argument | Default | Description |
|---|---|---|
| `--host` | `https://rez.rezbotapp.com` | Base URL for Rez API calls |
| `--webhook-host` | same as `--host` | Public URL Telegram sends webhooks to (Cloudflare tunnel) |
| `--name` | required | Full facility name |
| `--street` | required | Street address |
| `--city` | required | City and postal code |
| `--timezone` | `Europe/Berlin` | IANA timezone |
| `--token` | required | Telegram bot token |
| `--admins` | — | Comma-separated Telegram user IDs for facility admins |
| `--courts` | required | Comma-separated `"Court Name:calendarId"` pairs |

The script prints a summary with all generated IDs at the end.

---

## Step 4 — Record the generated IDs

Copy the output into the **"Current provisioned state"** table at the bottom of [provisioning.md](provisioning.md).
You will need the facility ID if you ever need to re-provision a court or update facility metadata.

---

## Step 5 — Verify

```shell
HOST=https://maxdc.duckdns.org
FACILITY_ID=<id from script output>
BOT_TOKEN=<bot token>

# Check facility entity — should show name, botToken, timezone, resourceIds
curl -s $HOST/facility/$FACILITY_ID | python3 -m json.tool

# Check webhook registration
curl -s "https://api.telegram.org/bot$BOT_TOKEN/getWebhookInfo" | python3 -m json.tool
```

Expected webhook response:
```json
{
  "url": "https://maxdc.duckdns.org/telegram/<token>/webhook",
  "has_custom_certificate": false,
  "pending_update_count": 0
}
```

---

## Step 6 — Smoke test

Send a message to the bot from Telegram:

> "What courts are available tomorrow at 6pm?"

The bot should reply within a few seconds. If it does not:
- Check `docker logs rez --tail 50` on lurch for errors
- Look for `"No facility found for bot token"` — this means the `FacilityByBotTokenView` has not populated yet (projection lag)
- Wait ~30 seconds and retry; projections catch up from the journal on startup

---

## Webhook re-registration

Re-run whenever the hostname changes (e.g. after a DNS update or new deployment target):

```shell
BOT_TOKEN=<token>
HOST=maxdc.duckdns.org

curl "https://api.telegram.org/bot$BOT_TOKEN/setWebhook?url=https://$HOST/telegram/$BOT_TOKEN/webhook"
```
