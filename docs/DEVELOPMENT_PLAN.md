# Development Plan

---

## Current blocker

### Standalone projections not running — allegedly fixed, needs verification

Rez is deployed self-managed on lurch (CT 115, `https://maxdc.duckdns.org`).
Entities write events to the PostgreSQL journal fine, but **views and consumers
never process any events** — `projection_offset` stays empty, views stay empty.

**Fix:** upgrade to the Akka SDK version that includes the patch — see
[akka/akka-sdk#1349](https://github.com/akka/akka-sdk/issues/1349).

**TODO: verify that projections are running after upgrading (April–May 2026).**

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

### Google Calendar consistency gap

When a booking is created, a calendar event is pushed to Google Calendar as a
fire-and-forget side-effect. There is no reverse path. If Rez state is wiped,
the calendar events are **not** deleted — stale bookings will show in the calendar.

Alternative: model the calendar as a true Akka View projecting from reservation
events, and send `.ics` email attachments instead, decoupling Rez from Google
Calendar entirely.

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
