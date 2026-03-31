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

## Step 2 — Google Calendars (automated)

The provisioning script creates one Google Calendar per court automatically via the
Calendar API, authenticated as the service account from `credentials.json`.
No manual Google Calendar UI steps are needed.

The service account becomes the **owner** of each created calendar. To also see the
calendars in your own Google account, go to **Settings → Sharing** and add your
personal email with "Make changes and manage sharing".

> **Using existing calendars:** if calendars already exist (e.g. manually created),
> pass them as `"Court Name:calendarId"` pairs in `--courts` instead of bare names.
> You can mix both forms in the same invocation.

---

## Step 3 — Run the provisioning script

The script creates the facility entity, registers each court, and registers the Telegram webhook in one shot.

```shell
./scripts/provision-facility.sh \
  --name        "Erster Tennisclub Edingen-Neckarhausen" \
  --street      "Mannheimer Str. 50" \
  --city        "68535 Edingen-Neckarhausen" \
  --token       "123456789:ABCdef..." \
  --admins      "987654321" \
  --credentials path/to/credentials.json \
  --courts      "Court 1,Court 2,Court 3,Court 4"
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
| `--credentials` | `$GOOGLE_CREDENTIALS_FILE` or `./credentials.json` | Path to service account credentials JSON |
| `--courts` | required | Comma-separated court names — script creates Google Calendars automatically. Pass `"Name:calendarId"` to use an existing calendar instead. |

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
