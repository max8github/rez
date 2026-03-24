# Rez — AI-Powered Court Booking

Rez lets tennis club members book courts by sending a natural language message
via a chat app. An AI agent interprets the request, checks availability, books
the court, and replies conversationally. Confirmed bookings appear automatically
in Google Calendar.

The messaging layer is pluggable. The current production integration is
[Telegram](https://telegram.org). [Matrix/Element](https://matrix.org) support
is partially implemented (`MatrixEndpoint`) but not active in production.

## Architecture

```
Member (chat app)
  │  natural language message
  ▼
Messaging service (currently: Telegram)
  │  POST /telegram/{botToken}/webhook  (or /matrix/message for Matrix)
  ▼
MessagingEndpoint  (Akka HTTP, e.g. TelegramEndpoint)
  │
  ▼
BookingAgent    (Akka Agent, GPT-4o-mini)
  │  tool calls
  ▼
BookingService  (Spring @Component)
  │  ComponentClient calls
  ├──▶ ResourceView      – check availability
  ├──▶ ReservationEntity – initiate booking
  └──▶ FacilityEntity    – broadcast to courts
          │
          ▼
       ResourceEntity (court-1, court-2)
          │  FULFILLED event
          ▼
       DelegatingServiceAction
          │
          ▼
       GoogleCalendar (Google Calendar API)
```

## Build

From `reservation/reservation/`:

```bash
mvn clean compile
```

The `googlecalendar` and other stub modules must be installed in the local Maven
repository first (one-time setup, or after a version bump). Run from `reservation/`
(the parent module) — **not** from `reservation/reservation/` to avoid triggering
the Docker image build:

```bash
cd /path/to/reservation  # the parent module, not reservation/reservation
mvn install -pl spi,calendarstub,notifierstub,googlecalendar,telegramnotifier,twistnotifier -DskipTests
```

To build and push a production image to the Gitea registry, run from `reservation/reservation/`:

```bash
mvn clean install -DskipTests -Pstandalone
docker tag reservation:1.0-<timestamp> gitea-reg.fritz.box:3000/max/rez:latest
docker push gitea-reg.fritz.box:3000/max/rez:latest
```

The `-Pstandalone` profile (from `akka-javasdk-parent`) builds a production JIB image —
dev-mode disabled, correct main class. The timestamp suffix is printed at the end of the
Maven build output. There is no Dockerfile; the image is built entirely by the Maven plugin.

## Run

```bash
# Production (default): uses real Google Calendar API
mvn exec:java

# Local dev / testing: uses FakeCalendarSender (no Google API calls)
mvn compile exec:java -Plocal
```

Service starts on `http://localhost:9000`.

### Environment variables

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | Yes | OpenAI API key (used by BookingAgent) |

To switch LLM provider, change `model-provider` in `application.conf`.

### Google Calendar credentials

Place the service account key at `secrets/credentials.json` (relative to
`reservation/reservation/`). The file is gitignored. The service account is
`kalix-rez@rezcal.iam.gserviceaccount.com` and must have write access to both
court calendars.

## Provisioning

State is in-memory — the facility and courts must be reprovisioned on every
restart. The facility ID must match the one registered against the Telegram bot
token (via `FacilityByBotTokenView`).

```bash
# Create facility (ID must match bot's FACILITY_ID)
curl -X POST http://localhost:9000/facility/4463962 \
  -H "Content-Type: application/json" \
  -d '{"name":"Erster Tennisclub Edingen-Neckarhausen","address":{"street":"Mannheimer Str. 50","city":"68535 Edingen-Neckarhausen"}}'

# Register courts (creates resource entity AND links it to the facility)
curl -X POST http://localhost:9000/facility/4463962/resource/court-1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Court 1"}'

curl -X POST http://localhost:9000/facility/4463962/resource/court-2 \
  -H "Content-Type: application/json" \
  -d '{"name":"Court 2"}'
```

> Use `POST /facility/{id}/resource/{rid}` (not the two-step
> `POST /resource` + `PUT /facility/.../resources/...`) — only this endpoint
> sets `facilityId` correctly in the ResourceView, which is required for
> availability checks.

## Messaging integration

Rez exposes one HTTP endpoint per supported messaging service. All endpoints
follow the same pattern: receive an incoming message, invoke the BookingAgent
asynchronously, and send the reply back via the corresponding `NotificationSender`
implementation.

### Telegram (current production integration)

`TelegramEndpoint` receives webhook POSTs from Telegram at
`POST /telegram/{botToken}/webhook`. The bot token in the path is used to look
up the facility via `FacilityByBotTokenView`, so one Rez deployment can serve
multiple facilities with different bots — no `FACILITY_ID` env var needed.

**Required env var:**

| Variable | Description |
|---|---|
| `TELEGRAM_BOT_TOKEN` | Bot token from [@BotFather](https://t.me/botfather), used by `TelegramNotifier` to send replies |

**One-time webhook registration** (run after each deploy):

```bash
curl "https://api.telegram.org/bot{TOKEN}/setWebhook?url=https://{rez-host}/telegram/{TOKEN}/webhook"
```

Rez must be reachable from the public internet over HTTPS for Telegram to deliver webhooks.

### Matrix (partial implementation, not active in production)

`MatrixEndpoint` at `POST /matrix/message` exists but is synchronous and lacks
a `matrixnotifier` module. See `REVIEW_NOTES.md` for what remains to be done.
Matrix worked on LAN but was not successfully exposed externally.

### LAN-only workaround (socat)

Akka binds to `localhost` by default. When running on your Mac and the bot runs
on a separate container, use socat to expose the service on the LAN interface:

```bash
socat TCP-LISTEN:9000,bind=192.168.178.82,fork TCP:127.0.0.1:9000 &
```

Replace `192.168.178.82` with your Mac's LAN IP. This goes away when Rez is
deployed behind a reverse proxy (nginx/caddy) or configured to bind to
`0.0.0.0`.

## Deployment

Rez is deployed on lurch (CT 113, `192.168.178.50`) as a Docker service. See the
[mini-dc](https://gitea-ssh.fritz.box/max/mini-dc) repo for the full deployment
setup, including:
- `compose.yaml` — the `rez` service definition
- `env/prod/rez.env` (or equivalent) — environment variables (`OPENAI_API_KEY`, `TELEGRAM_BOT_TOKEN`, etc.)
- Instructions for placing `secrets/credentials.json` on the host

## Google Calendar embed

Both court calendars can be embedded together as an iframe:

```html
<iframe id="rezCal"
  src="https://calendar.google.com/calendar/u/0/embed?src=3d228lvsdmdjmj79662t8r1fh4%40group.calendar.google.com&ctz=Europe%2FBerlin&src=63hd39cd9ppt8tajp76vglt394%40group.calendar.google.com"
  style="border:0" width="800" height="600" frameborder="0" scrolling="no">
</iframe>
<script>
  // Reload the calendar iframe every 2 minutes
  setInterval(() => { const c = document.getElementById('rezCal'); c.src = c.src; }, 120000);
</script>
```

Both calendars must be set to **public** in Google Calendar settings.
Note: Google Calendar typically takes 1–5 minutes to reflect new events after
they are created via the API.

## Integration Tests

```bash
mvn verify -Pit
```
