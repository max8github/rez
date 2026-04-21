# Development Plan

---

## Current blocker

No single blocker currently tracked here.

---

## Backlog

### Make MatrixEndpoint fully async

`MatrixEndpoint` returns the agent reply synchronously in the HTTP response body,
which `bot.py` then posts to the Matrix room. `TelegramEndpoint` and `TwistEndpoint`
are fully async: they ack immediately and send replies via `NotificationSender`.

To align Matrix:
- Create a `matrixnotifier` module implementing `NotificationSender` that calls the
  Matrix Client-Server API. Needs a Matrix access token and room ID as `recipientId`.
- Update `MatrixEndpoint` to use `invokeAsync` + `notificationSender.send()` and
  return `void`, same pattern as `TelegramEndpoint`.
- `bot.py` no longer needs to handle the reply.

### Move messaging endpoints out of the `reservation` module

`TelegramEndpoint`, `MatrixEndpoint`, and `TwistEndpoint` live in the main
`reservation` module, giving it compile-time knowledge of three external protocols.
Each should move to its own module (`telegramendpoint` etc.), mirroring how
notification senders already live in `telegramnotifier`, `twistnotifier`, `notifierstub`.
Transport wiring would then be controlled entirely by classpath.

### Provisioning race before bookings

Facility resource registration is asynchronous. If bookings arrive immediately
after facility creation but before resource registration completes, availability
fan-out may see an empty resource set and the booking will time out. This is now
handled safely, but the provisioning flow should eventually expose readiness more clearly.

### Google Calendar is still not a source of truth

Rez now has a true read-only calendar view built from reservation events, but
Google Calendar remains a side-effect mirror. If Google API calls fail, Rez and
Google can still diverge. The internal consistency problem is solved for Rez’s
own calendar, not for the external Google mirror.

### Reservation duration is still implicit

The Rez calendar currently renders each reservation as a one-hour block because
Rez persists a single booking timestamp rather than an explicit duration. If
different slot lengths are needed, duration must become first-class reservation data.

### `effects().done()` reliability in consumers

Akka SDK consumers call `componentClient.invoke()` fire-and-forget and return
`effects().done()`. If the downstream call fails, the consumer has already ack'd
the event and will not retry. Acceptable for now; worth revisiting if reliability
becomes a concern.

---

## Done

### Provisioning sprint

- `calendarId` field on resources (passed at creation, stored on `ResourceEntity`)
- `timezone`, `botToken`, `adminUserIds` fields on `FacilityEntity`
- `FacilityByBotTokenView` — look up facility by bot token; enables multi-facility
  routing without a `FACILITY_ID` env var
- `POST /facility` auto-generates facility UUID (callers never supply IDs)
- `POST /facility/{id}/resource` auto-generates resource UUID
- `build-push.sh` — single script for both standalone (lurch) and cloud targets
- `ProvisioningIntegrationTest` — integration tests for all new fields and the view

### Rez read-only calendar

- `ReservationCalendarView` — Akka View projecting fulfillment/cancellation events
- `CalendarEndpoint` — serves `GET /calendar` and `GET /api/calendar/events`
- static `calendar.html` UI served directly from the service
- integration test coverage for calendar view fulfillment/cancellation behavior
- Rez now offers a state-derived read-only calendar in parallel with Google Calendar

### Kalix → Akka SDK 3.x migration

Full-platform rewrite at the framework level. Key changes:

| Kalix | Akka SDK 3.x |
|---|---|
| `@TypeId`/`@Id` + `@RequestMapping` | `@Component(id = "...")` |
| `extends Action` + `@Subscribe.EventSourcedEntity` | `extends Consumer` + `@Consume.FromEventSourcedEntity` |
| `@EventHandler` per event type | Single `applyEvent()` with `switch` |
| `effects().emitEvent()` | `effects().persist()` |
| `effects().forward(deferredCall)` | `componentClient.method().invoke()` + `effects().done()` |
| Views: `Flux<T>` + per-event `@Subscribe` | `TableUpdater<T>` inner class + `@Consume` |
| `SpringBootApplication` + `main()` | `Bootstrap implements ServiceSetup` |
| Spring Boot parent + Kalix plugin | `io.akka:akka-javasdk-parent` |

### application.conf — no on-prem overrides needed

`runtime-standalone.conf` (inside the Akka runtime JAR) already contains the correct
r2dbc connection-factory block with `${?DB_HOST}` substitutions and ends with
`include "application"`. No r2dbc block is needed in `application.conf`.

### Dockerfile removed

There is no Dockerfile. The `akka-javasdk-parent` uses JIB to build the image via
`mvn install -Pstandalone`. Use `./build-push.sh`.
