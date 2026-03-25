# Rez — AI-Powered Court Booking

Rez lets tennis club members book courts by sending a natural language message
via Telegram. An AI agent interprets the request, checks availability, books the
court, and replies conversationally. Confirmed bookings appear in Google Calendar.

## Architecture

```
Member (Telegram)
  │  natural language message
  ▼
TelegramEndpoint   POST /telegram/{botToken}/webhook
  │
  ▼
BookingAgent       (Akka Agent, GPT-4o-mini)
  │  tool calls
  ▼
BookingService
  │  ComponentClient calls
  ├──▶ ResourceView       – check availability
  ├──▶ ReservationEntity  – initiate booking
  └──▶ FacilityEntity     – broadcast to courts
            │
            ▼
         ResourceEntity (court-1, court-2, …)
            │  FULFILLED event
            ▼
         DelegatingServiceAction
            │
            ▼
         GoogleCalendar (Google Calendar API)
```

The bot token in the webhook path is used to look up the facility via
`FacilityByBotTokenView` — one deployment can serve multiple clubs with
different bots, no `FACILITY_ID` env var needed.

## Stack

- **Runtime**: [Akka SDK](https://akka.io) 3.x (Java), event-sourced entities,
  views, consumers, agents
- **LLM**: OpenAI GPT-4o-mini (switchable via `application.conf`)
- **Persistence**: PostgreSQL via R2DBC (standalone) or managed (Akka Cloud)
- **Messaging**: Telegram (production), Matrix/Twist (partial)
- **Calendar**: Google Calendar API via service account

## Modules

| Module | Purpose |
|---|---|
| `reservation` | Main application — entities, views, endpoints, agent |
| `spi` | `CalendarSender` and `NotificationSender` interfaces |
| `googlecalendar` | Google Calendar implementation of `CalendarSender` |
| `telegramnotifier` | Telegram implementation of `NotificationSender` |
| `twistnotifier` | Twist implementation of `NotificationSender` |
| `calendarstub` | No-op stub for local dev |
| `notifierstub` | No-op stub for local dev |

## Deployment

Rez runs self-managed on lurch (home server, `https://maxdc.duckdns.org`),
deployed as a Docker service on Proxmox LXC CT 115 with a PostgreSQL sidecar.

Akka Cloud (`max8github/rez` on Docker Hub) is the fallback — see
[docs/deployment.md](docs/deployment.md).

## Quick start (local dev)

```shell
cd reservation/reservation
mvn compile exec:java -Plocal   # stub calendar + notifier, no external calls
```

See [docs/quick-notes-runbook.md](docs/quick-notes-runbook.md) for copy-paste
curl commands to provision a facility and send test bookings.

## Provisioning

State is persistent (PostgreSQL). Facilities and courts are provisioned once via
the HTTP API and survive restarts. See [docs/provisioning.md](docs/provisioning.md).

## Docs

| File | Contents |
|---|---|
| [docs/deployment.md](docs/deployment.md) | Build, push, deploy — standalone and cloud; current status |
| [docs/provisioning.md](docs/provisioning.md) | How to onboard a new club |
| [docs/quick-notes-runbook.md](docs/quick-notes-runbook.md) | Local dev copy-paste commands |
| [docs/user-onboarding.md](docs/user-onboarding.md) | End-user booking guide |
| [reservation/docs/DEVELOPMENT_PLAN.md](reservation/docs/DEVELOPMENT_PLAN.md) | Development plan: current blocker, backlog, completed work |
| [reservation/reservation/README.md](reservation/reservation/README.md) | Module-level build and run reference |
