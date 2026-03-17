# Rez — AI-Powered Court Booking

Rez lets tennis club members book courts by sending a natural language message
via [Matrix/Element](https://matrix.org). An AI agent interprets the request,
checks availability, books the court, and replies conversationally. Confirmed
bookings appear automatically in Google Calendar.

## Architecture

```
Member (Element app)
  │  natural language message
  ▼
Matrix bot (bot.py, CT 113)
  │  POST /matrix/message
  ▼
MatrixEndpoint  (Akka HTTP)
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
repository first (one-time setup, or after a `clean`):

```bash
cd ../../spi          && mvn package && mvn install:install-file -Dfile=target/spi-0.5.jar            -DgroupId=com.rezhub.reservation -DartifactId=spi            -Dversion=0.5 -Dpackaging=jar
cd ../notifierstub    && mvn package && mvn install:install-file -Dfile=target/notifierstub-0.5.jar   -DgroupId=com.rezhub.reservation -DartifactId=notifierstub    -Dversion=0.5 -Dpackaging=jar
cd ../googlecalendar  && mvn package && mvn install:install-file -Dfile=target/googlecalendar-0.5.jar -DgroupId=com.rezhub.reservation -DartifactId=googlecalendar  -Dversion=0.5 -Dpackaging=jar
cd ../reservation
```

## Run

```bash
# Production (default): uses real Google Calendar API
mvn exec:java

# Local dev / testing: uses FakeCalendarSender (no Google API calls)
mvn exec:java -Plocal
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
restart. The facility ID must match `FACILITY_ID` in the Matrix bot config.

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

## Matrix bot

The bot lives in `mini-dc/stacks/matrix/` and runs on CT 113 (lurch,
`192.168.178.50`). It forwards every room message to `POST /matrix/message` on
Rez. No trigger prefix — every message in the booking room is sent to the agent.

Key env vars in `mini-dc/env/prod/matrix.env` (gitignored):

| Variable | Description |
|---|---|
| `REZ_URL` | Base URL of the Rez service, e.g. `http://192.168.178.82:9000` |
| `FACILITY_ID` | Facility ID without `f_` prefix, e.g. `4463962` |
| `MATRIX_USER_ID` | Bot's Matrix ID, e.g. `@ollama:fritz.box` |
| `MATRIX_PASSWORD` | Bot's Matrix password |

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

Rez is deployed on lurch as a Docker service alongside Matrix. See the
`stacks/matrix/` stack in the [mini-dc](https://gitea-ssh.fritz.box/max/mini-dc)
repo for the full deployment setup, including:
- `compose.yaml` — the `rez` service definition
- `env/prod/matrix.env` — environment variables (OPENAI_API_KEY, paths)
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
