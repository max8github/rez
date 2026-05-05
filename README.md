# Rez — AI-Powered Court Booking

Rez lets tennis club members book courts by sending a natural language message
via Telegram. An AI agent interprets the request, checks availability, books the
court, and replies with a confirmation.

Rez also serves a read-only calendar view derived from reservation events.

## Docs map

Start with these docs:

- [docs/README.md](docs/README.md) — docs structure and source-of-truth guide
- [docs/reference/rez-system-overview.md](docs/reference/rez-system-overview.md) — current-state architecture
- [docs/conceptual-orchestration-overview.md](docs/conceptual-orchestration-overview.md) — conceptual orchestration overview
- [reservation/reservation/docs/booking-flow.md](reservation/reservation/docs/booking-flow.md) — booking flow
- [reservation/reservation/docs/fsm.md](reservation/reservation/docs/fsm.md) — reservation state machine
- [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) — active engineering follow-up work

## Where to read next

If you are new to the project:

1. Read [docs/reference/rez-system-overview.md](docs/reference/rez-system-overview.md)
2. Then read [docs/conceptual-orchestration-overview.md](docs/conceptual-orchestration-overview.md)
3. Then read [reservation/reservation/docs/booking-flow.md](reservation/reservation/docs/booking-flow.md) and [reservation/reservation/docs/fsm.md](reservation/reservation/docs/fsm.md)

If you need a specific sub-topic:

- Current implemented architecture: [docs/reference/rez-system-overview.md](docs/reference/rez-system-overview.md)
- Conceptual orchestration layering: [docs/conceptual-orchestration-overview.md](docs/conceptual-orchestration-overview.md)
- Exact lock point and race behavior: [docs/reference/reservation-locking.md](docs/reference/reservation-locking.md)
- Booking lifecycle and candidate exhaustion flow: [reservation/reservation/docs/booking-flow.md](reservation/reservation/docs/booking-flow.md)
- Reservation state machine: [reservation/reservation/docs/fsm.md](reservation/reservation/docs/fsm.md)
- Deployment: [docs/deployment.md](docs/deployment.md) and [docs/deployment-standalone.md](docs/deployment-standalone.md)
- Provisioning and facility onboarding: [docs/provisioning.md](docs/provisioning.md) and [docs/facility-provisioning-runbook.md](docs/facility-provisioning-runbook.md)
- Local dev smoke commands: [docs/quick-notes-runbook.md](docs/quick-notes-runbook.md)
- Active follow-up engineering work: [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md)

## Architecture

```
Member (Telegram)
  │  natural language message
  ▼
TelegramEndpoint   POST /telegram/{botToken}/webhook
  │  builds OriginRequestContext
  ▼
BookingAgent       (Akka Agent, GPT-4o-mini)
  │  tool calls
  ▼
BookingTools
  │
  ▼
BookingApplicationService
  │  resolves BookingContext, selects workflow
  ▼
CourtBookingWorkflow
  ├──▶ BookingContextResolverAkka  – botToken → facilityId + timezone
  ├──▶ CourtDirectoryAkka          – facilityId → candidate resourceIds
  └──▶ ReservationGatewayAkka      – submit / cancel / get
            │
            ▼
        ReservationEntity
            │
            ├──▶ ReservationAction
            │      └── fan-out checkAvailability / reserve → ResourceEntity
            │
            ├──▶ ReservationOutcomeProducer
            │      └── emits reservation-outcomes service stream
            │
            └──▶ DelegatingServiceAction
                   └── Telegram notification + optional Rez calendar link

ResourceEntity
  └──▶ ResourceView / ResourcesByFacilityView / ReservationCalendarView
                           └──▶ CalendarEndpoint  GET /calendar
```

The bot token in the webhook path is used to look up the facility via
`FacilityByBotTokenView` — one deployment can serve multiple clubs with
different bots, no `FACILITY_ID` env var needed.

## Stack

- **Runtime**: [Akka SDK](https://akka.io) 3.x (Java), event-sourced entities,
  views, consumers, agents
- **LLM**: OpenAI GPT-4o-mini (switchable via `application.conf`)
- **Persistence**: managed PostgreSQL (Akka Cloud)
- **Messaging**: Telegram

## Modules

| Module | Purpose |
|---|---|
| `reservation` | Main application — entities, views, endpoints, orchestration, agent |
| `spi` | `CalendarSender` and `NotificationSender` interfaces |
| `telegramnotifier` | Telegram implementation of `NotificationSender` |
| `notifierstub` | No-op stub for local dev |

## Deployment

Rez runs on **Akka Cloud** (`max8github/rez` on Docker Hub, project `rez-prod`).

A standalone self-managed deployment on lurch (Proxmox LXC CT 115) exists as fallback —
see [docs/deployment.md](docs/deployment.md) for both options.

## Quick start (local dev)

```shell
cd reservation/reservation
mvn compile exec:java -Plocal   # stub notifier, no external calls
```

See [docs/quick-notes-runbook.md](docs/quick-notes-runbook.md) for copy-paste
curl commands to provision a facility and send test bookings.

Rez also exposes a read-only calendar UI at:

```text
http://localhost:9001/calendar?facilityId=<FACILITY_ID>
```

## Provisioning

State is persistent (managed PostgreSQL). Facilities and courts are provisioned once via
the HTTP API and survive restarts. See [docs/provisioning.md](docs/provisioning.md).

## Docs

| File | Contents |
|---|---|
| [docs/deployment.md](docs/deployment.md) | Build, push, deploy — Akka Cloud and standalone fallback |
| [docs/provisioning.md](docs/provisioning.md) | How to onboard a new club |
| [docs/facility-provisioning-runbook.md](docs/facility-provisioning-runbook.md) | Step-by-step facility onboarding |
| [docs/quick-notes-runbook.md](docs/quick-notes-runbook.md) | Local dev copy-paste commands |
| [docs/user-onboarding.md](docs/user-onboarding.md) | End-user booking guide |
| [docs/reference/rez-system-overview.md](docs/reference/rez-system-overview.md) | Current-state architecture overview |
| [docs/conceptual-orchestration-overview.md](docs/conceptual-orchestration-overview.md) | Conceptual orchestration layering overview |
| [docs/reference/reservation-locking.md](docs/reference/reservation-locking.md) | Precise locking and race-handling explanation |
