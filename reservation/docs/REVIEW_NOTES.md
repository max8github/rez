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

`ReservationAction`, `ResourceAction`, `RezAction`, `TimerAction`, and `WebhookAction` were all renamed conceptually to "consumers" (they extend `Consumer` now). The main behavioral change: they used to return `effects().forward(deferredCall)` to chain calls; now they invoke `componentClient` directly and return `effects().done()`. This is fire-and-forget at the consumer level — the downstream call is made but the consumer doesn't wait on or propagate its result.

**Main → Bootstrap:**

`Main.java` (Spring Boot entry point with `SpringApplication.run()`) was deleted. Replaced by `Bootstrap.java` which implements `ServiceSetup` and provides `createDependencyProvider()` — a Spring `AnnotationConfigApplicationContext` that Akka uses to resolve constructor-injected dependencies. This is the boundary between Akka's DI and Spring's DI.

---

## New HTTP Endpoints

Three new endpoints were added: `FacilityEndpoint`, `ResourceEndpoint`, `UserEndpoint` — all under `com.rezhub.reservation.api`. Previously the entities were exposed directly via `@RequestMapping` annotations (Kalix-style). Now the entities have no HTTP mapping at all; the endpoints are separate classes that use `ComponentClient` to call them.

This is the correct Akka pattern (endpoint → component client → entity), but it means the old curl provisioning commands against `/facility/{id}/create` etc. still work because the paths were preserved.

---

## Integration Tests

Old integration tests (`IntegrationTest.java`, `WebClientUtil.java`) were deleted — they used the Kalix test harness and raw HTTP calls. Replaced by `ReservationIntegrationTest.java` which extends `TestKitSupport` (Akka 3) and uses `componentClient` directly. This is the correct pattern per the coding guidelines.

---

## New material (not core Rez logic)

- **`akka-context/`** — a large dump of Akka SDK reference docs (html converted to markdown). Used as context for AI coding assistance (see `CLAUDE.md`). No runtime impact.
- **`shopping-cart-quickstart/`** — a standalone Akka SDK sample project. Not part of the booking system; added as a reference for correct patterns.
- **`CLAUDE.md`** — added to the repo root, pointing Claude at the akka-context docs and coding guidelines.

---

## Open questions on the migration

**`effects().done()` vs `effects().forward()` in consumers:** The old Kalix consumers used `forward()` to propagate the downstream call within the same transaction context. The new consumers call `componentClient.invoke()` fire-and-forget and then return `effects().done()`. This changes the error semantics: if the downstream call fails, the consumer has already ack'd the event. Is this acceptable? The consumer will not retry on downstream failure.

**Spring context scan scope:** `Bootstrap` scans `com.rezhub.reservation`. The sub-modules (`googlecalendar`, `notifierstub`) live in the same base package, so they are picked up if on the classpath. This means the Maven profile (google vs. local) directly controls which beans get registered — a somewhat implicit mechanism.

---

# Commit 9587409b — telegram-mvp squash

Open questions and concerns to address post-demo. Not urgent fixes, just recorded for clarity.

---

## Calendar

**Is the Google Calendar a reliable reflection of Rez state?**

Currently: when a booking is created in Rez, a calendar event is pushed to Google Calendar as a side-effect (fire-and-forget). There is no reverse path.

The concern: if Rez state is wiped (it happened before — in-memory store restarted), the calendar events are **not** deleted. The calendar will show bookings that no longer exist in Rez, and Rez will have no record of them. This is a consistency gap.

The current setup is not a View in the Akka sense — it is an outbound notification, not a projection from Rez's event log.

**Possible alternative**: instead of pushing events to Google Calendar on booking, model the calendar as a true Akka View (projecting from reservation events), and simply send out `.ics` email attachments so members can import events into their own calendars. This decouples Rez from Google Calendar entirely and avoids the stale-data problem.

---

## Bootstrap

**Is Spring DI working correctly after the changes?**

`Bootstrap.java` was modified to manually register `ComponentClient` and `TimerScheduler` as Spring singletons before `context.refresh()`, so that `BookingService` (a `@Component`) can `@Autowired` them. This is a non-standard pattern — worth verifying that it works correctly under all startup conditions (e.g. after a crash/restart, or if Akka initializes these beans lazily).

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

# Architecture: Messaging layer refactoring (backlog)

## Telegram module

`TelegramEndpoint` currently lives in the main `reservation` module and hardwires the Telegram transport (bot token, `sendMessage` API call). To make messaging pluggable the same way `CalendarSender` is, Telegram should become its own module like `twistnotifier`.

**Target architecture:**

- Main module contains thin HTTP endpoints that receive messages from any service, call `BookingAgent`, then delegate the reply to `NotificationSender`.
- A `telegram` module implements `NotificationSender` for Telegram (calls `api.telegram.org/sendMessage`). Can use standard `java.net.http.HttpClient` — no Akka SDK dependency needed.
- `notifierstub` serves as the local/test implementation (logs instead of sending).
- Which implementation is active is controlled purely by what's on the classpath (Maven profile or module inclusion), exactly like `CalendarSender`.

**What to remove:**

- `Assembler` SPI — obsolete. The LLM understands raw natural-language text directly. `TwistAssembler` and `TextMessageParser`/`StringNlpParser` were only needed when the old `WebhookAction` had to parse text manually.
- `Parser` SPI — same reason. Both can be deleted along with the `stringparser` module once `WebhookAction` is updated to call `BookingAgent` instead of `Parser`.
- `Nlp` interface from `spi` — already agreed to remove.

**`NotificationSender` signature needs generalising:**

Current signature is Twist-specific (`messageTwist(HttpClient, String body)` with Twist JSON). Should become something like:

```java
CompletableFuture<String> send(String recipientId, String text);
```

Where `recipientId` is whatever identifies the destination in that service (Telegram chat_id, Twist thread_id, etc.).

**`WebhookAction` (Twist inbound) needs updating:**

Currently bypasses the AI entirely — it parses the text with `Parser`/`Assembler` and creates a `ReservationEntity` directly. Should be updated to call `BookingAgent` like `TelegramEndpoint` does, then use `NotificationSender` to send the reply back to Twist.

---

## Premature booking confirmation (correctness issue)

`BookingService.bookCourt()` returns `"Booking confirmed! Reservation ID: ..."` after `ReservationEntity::init` completes — but `init` only persists the `Inited` event, putting the reservation in state `COLLECTING`. **No court has been assigned yet.**

The actual court assignment (availability broadcast protocol across `ResourceEntity` instances) happens asynchronously afterward, driven by `ReservationAction` (Consumer). Only when `ReservationEvent.Fulfilled` is emitted is the court truly reserved. If all courts are busy, `SearchExhausted` is emitted instead — and the user was already told "Booking confirmed."

**In practice**: the protocol runs fast (in-memory, milliseconds) so the lie is short-lived. For the MVP it is acceptable. For production, `bookCourt` should ideally wait for the `Fulfilled` event before returning — or at minimum the reply should say "Booking initiated, ID: abc123" rather than "confirmed."
