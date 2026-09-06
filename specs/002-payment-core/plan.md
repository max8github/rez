# Implementation Plan: Payment Core

**Branch**: `002-payment-core` | **Date**: 2026-09-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/002-payment-core/spec.md`

## Summary

Introduce Rez's first payment primitive: a Stripe hold anchored to each reservation's *commitment
cutoff* (`max(bookingTime, slotStart − commitmentWindow)`), captured by default at the *resolution
point* (`slotStart`). Four new components (`PaymentEntity`, `PlayerPaymentProfile`, `PricingPolicy` as
an embedded value on `FacilityEntity`/`ResourceEntity`, `SlotPaymentView`) plus a new
`StripeWebhookEndpoint` and `StripeService` infrastructure client, all inside the existing
`reservation` Maven module. `CourtBookingWorkflow.book()` gains two booking-time gates (player card-on-
file, facility Stripe-onboarding completeness) via a shared `PaymentGate`, and a post-fulfillment
payment-scheduling path via a new Consumer reacting to `Fulfilled`. `BookingEndpoint`'s separate direct
HTTP path — which bypasses `CourtBookingWorkflow` entirely and carries no player identity — also calls
`PaymentGate` for the facility-side check only (research.md #10); its lack of a player-identity concept
is a named, pre-existing limitation this feature surfaces but does not resolve (spec.md Out of Scope).
Rescue refund and the waiting list (Phases 2/3 of the source design
doc) are explicitly not built here — `SlotPaymentView` exists but has no consumer yet, and
`PaymentEntity`'s `VOIDED`/`REFUNDED` states are declared but undriven (FR-017).

## Technical Context

**Language/Version**: Java 21, Akka SDK 3.5.13 (matches `reservation` module's current pom)
**Primary Dependencies**: `com.stripe:stripe-java:27.1.0` (new — matches `hit-backend`'s pinned version, see research.md #7)
**Storage**: Akka Event Sourced Entity (`PaymentEntity`, new), Akka Key Value Entity (`PlayerPaymentProfileEntity`, new), existing `FacilityEntity`/`ResourceEntity`/`ReservationEntity` (extended with new fields), new Akka View (`SlotPaymentView`)
**Testing**: JUnit 5 + `EventSourcedTestKit` (`PaymentEntity`), `KeyValueEntityTestKit` (`PlayerPaymentProfileEntity`), `TestKitSupport`/`httpClient` (`StripeWebhookEndpoint`, pricing-policy endpoints), `Awaitility` (`SlotPaymentView`) — matches existing module conventions
**Target Platform**: Akka SDK service (same as rest of `reservation` module)
**Project Type**: Single Maven module (`reservation`) — no new module (research.md #1)
**Performance Goals**: N/A — payment operations are Timer-driven background work, not on the hot path of a player's Telegram request/response turnaround (the one exception, card-on-file/onboarding checks in `book()`, are simple in-memory/entity-state reads, not new external calls beyond what `PlayerPaymentProfile`'s own lookup already requires)
**Constraints**: Commitment-cutoff hold creation and resolution-point capture MUST be off-session and unattended (FR-007/FR-008) — no player interaction possible at either point, unlike Hit's client-confirmed flow (research.md #7). `commitmentWindow` MUST stay safely under Stripe's ~7-day authorization capture limit (FR-011).
**Scale/Scope**: 5 new components (`PaymentEntity`, `PlayerPaymentProfileEntity`, `SlotPaymentView`, `StripeService`, `PaymentGate`), 1 new endpoint (`StripeWebhookEndpoint`), 2 new Consumers/TimedActions (`PaymentSchedulingAction`, `CommitmentCutoffTimedAction` — the latter handles both commitment-cutoff hold creation and resolution-point capture as two methods on one class, not two components), 7 touched existing files (`FacilityState`/`FacilityEntity`/`FacilityEvent`, `ResourceState`/`ResourceEvent`, `ReservationState`, `CourtBookingWorkflow`, `BookingWorkflow`, `BookingEndpoint`, `FacilityEndpoint`/`ResourceEndpoint`)

## Constitution Check

*Gate: re-checked after Phase 1 design below.*

Against `rez`'s constitution (v1.0.0):

- **I. Akka SDK First**: No violation. All new components are Akka SDK primitives (Event Sourced
  Entity, Key Value Entity, View, Consumer, TimedAction, HTTP Endpoint). The one new external
  dependency (`stripe-java`) is justified: Stripe's own Java SDK is the documented, supported way to
  call Stripe's API — reimplementing PaymentIntent/Transfer/Connect HTTP calls by hand would be a worse
  outcome than reusing the vendor SDK, and `hit-backend` already established this exact dependency as
  acceptable for the same purpose.
- **II. Design Principles**: No violation. Domain records (`PaymentState`, `PricingPolicy`,
  `PlayerPaymentProfileState`) stay framework-free; `StripeService`/`StripeWebhookEndpoint` (the only
  places touching Stripe's SDK types) stay in `infrastructure`/`api`, matching `IdentityClient`'s
  existing precedent. `StripeWebhookEndpoint` defines its own request/response shapes, not exposing
  `PaymentState` directly. Single responsibility preserved: payment scheduling
  (`PaymentSchedulingAction`) is a separate component from notification (`DelegatingServiceAction`)
  rather than growing the latter (research.md #5); the two booking-time payability checks live in one
  shared `PaymentGate` rather than being duplicated across `CourtBookingWorkflow` and `BookingEndpoint`
  (research.md #10). Descriptive naming throughout — no generic `Event`/`Service`/`Manager` names.
- **III. Test Coverage**: Satisfied by design — every new component gets entity/view/endpoint-level
  tests per the existing per-component-type conventions (see Technical Context → Testing). Test plan
  detailed in tasks.md.
- **IV. Simplicity**: No violation, actively reinforced by three deliberate scope cuts already recorded
  in spec.md/research.md: (1) `PaymentEntity` implements only the transitions Phase 1 drives, leaving
  `VOIDED`/`REFUNDED` undriven rather than building unused command handlers (FR-017); (2) retry-attempt
  count for FR-016 is threaded through Timer call parameters rather than persisted as new entity state
  (research.md #6); (3) no new Maven module, reusing the existing `reservation` module and its
  established package conventions (research.md #1).

**Result**: PASS. No entries needed in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-payment-core/
├── spec.md               # Already complete (with Clarifications)
├── plan.md               # This file
├── research.md           # Phase 0 output
├── data-model.md          # Phase 1 output
├── quickstart.md          # Phase 1 output
├── contracts/
│   └── payment-api.md    # Phase 1 output — new external HTTP interfaces
└── tasks.md               # Phase 2 output (/akka.tasks — not created by this command)
```

### Source Code (repository root)

All within the existing `reservation` Maven module (`reservation/reservation/`) — no new module, no
new top-level directory:

```text
reservation/reservation/src/main/java/com/rezhub/reservation/
├── payment/                                    # NEW package
│   ├── PaymentState.java                       # NEW — domain record + State enum
│   ├── PaymentEvent.java                       # NEW — sealed interface: HoldAuthorized, HoldCaptured, HoldCreationFailed
│   ├── PaymentEntity.java                      # NEW — Event Sourced Entity: authorize/capture/fail command handlers
│   ├── PlayerPaymentProfileState.java          # NEW — domain record
│   ├── PlayerPaymentProfileEntity.java         # NEW — Key Value Entity: linkCustomer/setDefaultPaymentMethod
│   ├── PricingPolicy.java                      # NEW — embedded value record + FR-011 validation
│   ├── PaymentGate.java                        # NEW — shared FR-005/FR-012 checks (isPlayerPayable, isFacilityPayable), called from both CourtBookingWorkflow and BookingEndpoint (research.md #10)
│   ├── SlotPaymentView.java                    # NEW — Akka View, keyed by resourceId+dateTime
│   ├── PaymentSchedulingAction.java            # NEW — Consumer on ReservationEntity.Fulfilled, schedules commitment-cutoff Timer
│   └── CommitmentCutoffTimedAction.java        # NEW — TimedAction: hold creation + retry (FR-016), then schedules resolution-point Timer; also handles resolution-point capture and FR-010's grace-window follow-up
├── infrastructure/
│   ├── IdentityClient.java                     # Unchanged
│   └── StripeService.java                      # NEW — Stripe Java SDK wrapper, modeled on hit-backend's (research.md #7)
├── api/
│   ├── StripeWebhookEndpoint.java              # NEW — POST /webhooks/stripe
│   ├── FacilityEndpoint.java                   # MODIFIED — +2 PUT methods (pricing-policy, stripe-connected-account)
│   ├── ResourceEndpoint.java                   # MODIFIED — +1 PUT method (pricing-policy override)
│   ├── BookingEndpoint.java                    # MODIFIED — calls PaymentGate.isFacilityPayable() before ReservationEntity::init (FR-012 only — no player identity exists on this path, see research.md #10)
│   └── TelegramEndpoint.java                   # Unchanged (identity resolution already lands in OriginRequestContext per 001)
├── agent/
│   └── BookingTools.java                       # MODIFIED — bookCourt() surfaces the new card-setup-required / facility-not-payable outcomes distinctly instead of always "BOOKING_SUBMITTED:"
├── orchestration/
│   ├── BookingWorkflow.java                    # MODIFIED — book()'s return type changes from ReservationHandle to the new sealed BookingHandle (interface signature change, implemented by CourtBookingWorkflow)
│   └── CourtBookingWorkflow.java               # MODIFIED — book() gains FR-005/FR-012 gates via PaymentGate before reservationGateway.submit() (research.md #9/#10); return type changes to BookingHandle
├── customer/facility/
│   ├── FacilityState.java                      # MODIFIED — +2 fields: pricingPolicy, stripeConnectedAccountId
│   ├── FacilityEvent.java                      # MODIFIED — +2 event variants: PricingPolicySet, StripeConnectedAccountSet
│   └── FacilityEntity.java                     # MODIFIED — +2 command handlers
├── resource/
│   ├── ResourceState.java                      # MODIFIED — +1 field: pricingPolicyOverride
│   ├── ResourceEvent.java                      # MODIFIED — +1 event variant: PricingPolicyOverrideSet
│   └── ResourceEntity.java                     # MODIFIED — +1 command handler
└── reservation/
    ├── ReservationState.java                   # MODIFIED — +1 field: paymentId (Optional<String>)
    ├── ReservationEvent.java                   # Unchanged — paymentId is set via a separate PaymentSchedulingAction-issued command, not part of Fulfilled itself (see plan note below)
    └── ReservationEntity.java                  # MODIFIED — +1 command handler to record paymentId once commitment-cutoff processing begins
```

`reservation/reservation/pom.xml`: **MODIFIED** — adds `com.stripe:stripe-java:27.1.0` dependency,
module version bump per this project's Maven-versioning convention (committed separately from code,
per `feedback_maven_versioning`).

**Structure Decision**: New payment domain lives in its own `payment` package (mirroring the design
doc's own Code Mapping suggestion), touching six existing packages at their established extension
points (`with*()` builder methods, new sealed-interface event variants, one new command handler each).
No existing component is restructured or renamed. `CourtBookingWorkflow.book()`'s signature change
(`ReservationHandle` → a new sealed `BookingHandle`, on both `CourtBookingWorkflow` and the
`BookingWorkflow` interface it implements) is the one call-site ripple that reaches into
`BookingTools` — it already sits downstream of `book()` today and needs only a `switch` over the new
result type, not a redesign. `BookingEndpoint` is a separate, independent call site (it never went
through `book()` — see research.md #10) and needs its own small addition: a call to
`PaymentGate.isFacilityPayable()` before `ReservationEntity::init`, returning a `400` on rejection
rather than participating in `BookingHandle`'s switch at all.

### One deliberate design note for `/akka.tasks`

`ReservationEntity` needs a new command (e.g. `recordPaymentId(String paymentId)`) invoked by
`PaymentSchedulingAction` right before it schedules the commitment-cutoff Timer — not by attaching
`paymentId` to the existing `Fulfilled` event itself. Reasoning: `Fulfilled` is persisted by
`ReservationEntity.fulfill()`, called from `ResourceAction` at the moment a resource lock succeeds —
before any payment-side decision has been made about *whether* a `PaymentEntity` will even be created
for this reservation (e.g., no `PlayerPaymentProfile`/`PricingPolicy` would have blocked booking
earlier via FR-005/FR-012, but a null-price edge case or a scheduling failure could still mean payment
processing never actually starts). Keeping `paymentId` a separately-recorded fact — set only once
`PaymentSchedulingAction` has actually decided to proceed — avoids `ReservationEntity` ever claiming a
`paymentId` for a payment flow that didn't really begin.

## Complexity Tracking

*No violations — table intentionally omitted.*
