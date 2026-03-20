# Review Notes

---

# Commit 9b667cb1 — Merge branch `akka3migration`

This was the major Kalix → Akka SDK 3.x migration, plus new endpoints, tests, and reference material.
242 files changed; most of the bulk is docs (`akka-context/`) and a new sample (`shopping-cart-quickstart/`).
The real work is in the `reservation/` module.

---

## Kalix → Akka SDK 3.x migration (the core change)

This was a full-platform rewrite at the framework level, not a business logic change.
Everything still does the same thing; it just speaks a different API.

**Key naming/annotation changes:**

| Kalix | Akka SDK 3.x |
|---|---|
| `@TypeId` / `@Id` + `@RequestMapping` | `@Component(id = "...")` |
| `extends Action` + `@Subscribe.EventSourcedEntity` | `extends Consumer` + `@Consume.FromEventSourcedEntity` |
| `@EventHandler` methods per event type | Single `applyEvent()` override with `switch` |
| `effects().emitEvent(...)` | `effects().persist(...)` |
| `effects().forward(deferredCall)` | `componentClient.method(...).invoke(...)` + `effects().done()` |
| `kalixClient.forEventSourcedEntity(...).call(...)` | `componentClient.forEventSourcedEntity(...).method(...).invoke(...)` |
| `StatusCode.ErrorCode.BAD_REQUEST` in `effects().error()` | `effects().error(msg)` only (no status code) |
| `@Query` + `@Table` + `@ViewId` + `Flux<T>` return | `@Component` + `@Query` returning a wrapper record |
| `@Subscribe.EventSourcedEntity` on View methods | `extends TableUpdater<T>` with `@Consume.FromEventSourcedEntity` |
| `SpringBootApplication` + `main()` | `Bootstrap implements ServiceSetup` + `createDependencyProvider()` |

**Build / POM:**

The parent POM switched from `spring-boot-starter-parent` (Spring Boot 3.1.0) to `io.akka:akka-javasdk-parent:3.5.13`. The old kalix-maven-plugin, maven-shade-plugin, docker-maven-plugin, and Spring Boot plugin are all gone. This is a significant simplification of the build.

**Views:**

`ResourceView` and `FacilityByNameView` were fully rewritten. The old Kalix style used `Flux<T>` return types, per-event `@Subscribe` methods on the View class itself, and `@GetMapping` queries returning null. The new style uses a separate `TableUpdater<T>` inner class with `@Consume`, and a query method returning a record wrapper.

**Consumers (ex-Actions):**

`ReservationAction`, `ResourceAction`, `RezAction`, `TimerAction` were all renamed conceptually to "consumers" (they extend `Consumer` now). The main behavioral change: they used to return `effects().forward(deferredCall)` to propagate the downstream call within the same transaction context. The new consumers call `componentClient.invoke()` fire-and-forget and then return `effects().done()`. This changes the error semantics: if the downstream call fails, the consumer has already ack'd the event.

---

## New HTTP Endpoints

Three new endpoints were added: `FacilityEndpoint`, `ResourceEndpoint`, `UserEndpoint` — all under `com.rezhub.reservation.api`. Previously the entities were exposed directly via `@RequestMapping` annotations (Kalix-style). Now the entities have no HTTP mapping at all; the endpoints are separate classes that use `ComponentClient` to call them.

---

## Integration Tests

Old integration tests (`IntegrationTest.java`, `WebClientUtil.java`) were deleted — they used the Kalix test harness and raw HTTP calls. Replaced by `ReservationIntegrationTest.java` which extends `TestKitSupport` (Akka 3) and uses `componentClient` directly.

---

## New material (not core Rez logic)

- **`akka-context/`** — a large dump of Akka SDK reference docs (html converted to markdown). Used as context for AI coding assistance (see `CLAUDE.md`). No runtime impact.
- **`shopping-cart-quickstart/`** — a standalone Akka SDK sample project. Not part of the booking system; added as a reference for correct patterns.
- **`CLAUDE.md`** — added to the repo root, pointing Claude at the akka-context docs and coding guidelines.

---

## Open questions on the migration

**`effects().done()` vs `effects().forward()` in consumers:** The old Kalix consumers used `forward()` to propagate the downstream call within the same transaction context. If the downstream call fails, the consumer has already ack'd the event and will not retry. Is this acceptable?

---

# Commit 9587409b — telegram-mvp squash

---

## Calendar

**Is the Google Calendar a reliable reflection of Rez state?**

Currently: when a booking is created in Rez, a calendar event is pushed to Google Calendar as a side-effect (fire-and-forget). There is no reverse path.

The concern: if Rez state is wiped (it happened before — in-memory store restarted), the calendar events are **not** deleted. The calendar will show bookings that no longer exist in Rez, and Rez will have no record of them. This is a consistency gap.

The current setup is not a View in the Akka sense — it is an outbound notification, not a projection from Rez's event log.

**Possible alternative**: instead of pushing events to Google Calendar on booking, model the calendar as a true Akka View (projecting from reservation events), and simply send out `.ics` email attachments so members can import events into their own calendars. This decouples Rez from Google Calendar entirely and avoids the stale-data problem.

---

## application.conf

**Is the revised config specific to on-prem deployment, or is it a Kalix → Akka migration artifact?**

The file grew significantly: `akka.actor.provider = cluster`, R2DBC block, agent model config, `dev-mode.enabled = true`. Unclear which parts were needed to unblock Akka Cloud deployment specifically vs. which are inherent to the Akka SDK 3.x migration from Kalix. Worth a pass to understand what is truly required and what can be simplified or removed.

---

## Dockerfile

**Is the Dockerfile correct or too convoluted?**

The multi-stage build has several steps that feel fragile:
- Manual `mvn install -N` on the parent POM before sub-modules
- `settings.xml` copied from build context (gitignored, must be present at build time)
- Explicit `-Pgoogle` profile activation

Worth reviewing whether the build can be simplified, and whether the settings.xml approach is the right way to handle Akka repo credentials in CI/CD.

---

# Architecture: Messaging layer (backlog)

## MatrixEndpoint should be fully async

`MatrixEndpoint` currently returns the agent reply synchronously in the HTTP response body, which `bot.py` then posts to the Matrix room. The other two endpoints (Telegram, Twist) are fully async: they ack immediately and send all replies — both conversational and final booking outcomes — via `NotificationSender`.

To make Matrix consistent:
- Create a `matrixnotifier` module implementing `NotificationSender` that calls the Matrix homeserver Client-Server API (`PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message`). Needs a Matrix access token and the room ID as `recipientId`.
- Update `MatrixEndpoint` to use `invokeAsync` + `notificationSender.send()` and return `void`, same as `TelegramEndpoint` and `TwistEndpoint`.
- The Matrix bot (`bot.py`) no longer needs to handle the reply — it just fires the POST and forgets.

---

## Move messaging endpoints out of the `reservation` module

`TelegramEndpoint`, `MatrixEndpoint`, and `TwistEndpoint` currently live in the main `reservation` module. This means the module has compile-time knowledge of three external messaging protocols. Longer term, each should move to its own module (`telegramendpoint`, `matrixendpoint`, `twistendpoint`) the same way the notification senders live in `telegramnotifier`, `twistnotifier`, and `notifierstub`.

This would make the `reservation` module completely agnostic to which messaging services are in use, with transport wiring controlled entirely by which modules are on the classpath.
