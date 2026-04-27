# Rez Architecture Handoff

Cross-repository overview: [../../hit/hit-backend/docs/reference/backend-system-overview.md](../../hit/hit-backend/docs/reference/backend-system-overview.md)

Date: 2026-04-25

This document is the current-state handoff for Rez after the booking-flow refactors completed on 2026-04-22 through 2026-04-25.

It replaces the earlier speculative version of this memo with:

- what is now implemented
- what still exists as compatibility scaffolding
- what architectural risks remain real
- what follow-up work is still worth doing

Related documents:

- [docs/booking-orchestration-architecture.md](/Users/max/code/rez/docs/booking-orchestration-architecture.md)
- [reservation/reservation/docs/booking-flow.md](/Users/max/code/rez/reservation/reservation/docs/booking-flow.md)
- [reservation/reservation/docs/fsm.md](/Users/max/code/rez/reservation/reservation/docs/fsm.md)
- [docs/reservation-locking.md](/Users/max/code/rez/docs/reservation-locking.md)

## Executive Summary

The main architectural direction proposed in the earlier handoff has mostly been implemented.

What changed recently:

- timer-driven reservation completion was removed from the normal booking flow
- reservation search now starts from a flat candidate `resourceId` set
- booking completion is now determined by candidate exhaustion, not timeout
- `FacilityEntity` no longer acts as the resource container for booking
- resource-to-facility association now lives on the resource side via external reference metadata and `ResourcesByFacilityView`
- the old `BookingService` was split into a cleaner orchestration chain
- `TelegramEndpoint` is thinner and now passes `OriginRequestContext` into the agent path
- Rez now emits reservation outcome events for downstream consumers via a service stream

What did not change:

- facility still exists in Rez as a compatibility/helper concept for provisioning and Telegram routing
- Google Calendar side effects still exist
- Rez still serves its own read-only calendar UI
- orchestration is still not transactionally atomic across entity boundaries

The practical result is that Rez is now much closer to a narrow booking engine, but the integration surfaces still carry some transitional coupling.

## Current Architecture

At a high level the current runtime flow is:

```text
Telegram webhook or direct API caller
  │
  ├──▶ TelegramEndpoint
  │      └── builds OriginRequestContext and calls BookingAgent
  │
  └──▶ BookingEndpoint
         └── directly initializes ReservationEntity from flat resourceIds

BookingAgent
  │
  ▼
BookingTools
  │
  ▼
BookingApplicationServiceImpl
  │
  ▼
CourtBookingWorkflow
  │
  ├──▶ CourtDirectoryAkka
  │      └── resolves facility scope to candidate resourceIds
  │
  └──▶ ReservationGatewayAkka
         └── wraps ReservationEntity commands

ReservationEntity
  │
  ├──▶ ReservationAction
  │      └── fan-out checkAvailability / reserve
  │
  ├──▶ ReservationOutcomeProducer
  │      └── emits reservation-outcomes service stream events
  │
  └──▶ DelegatingServiceAction
         └── notification + Google Calendar side effects

ResourceEntity
  │
  └──▶ ResourceView / ResourcesByFacilityView / ReservationCalendarView
```

## What Was Implemented Recently

### 1. Timer-Driven Completion Removed

Commit:

- `44d1b4b refactor: remove timer-driven reservation completion`

This is the most important correctness change.

Normal booking failure is no longer decided by business timeout. Instead:

- the reservation starts with the full candidate set already known
- availability is fanned out across that fixed candidate set
- reserve is attempted against one concrete candidate at a time
- `UNAVAILABLE` is reached only when the known candidates are exhausted

This removes the old timer-based `SearchExhausted` race from the normal flow.

Relevant docs:

- [reservation/reservation/docs/booking-flow.md](/Users/max/code/rez/reservation/reservation/docs/booking-flow.md)
- [reservation/reservation/docs/fsm.md](/Users/max/code/rez/reservation/reservation/docs/fsm.md)
- [docs/reservation-locking.md](/Users/max/code/rez/docs/reservation-locking.md)

### 2. Booking Now Starts From Flat Resource IDs

This direction is now implemented in the reservation flow and external API.

Evidence:

- `BookingEndpoint` accepts a flat `Set<String> resourceIds`
- `ReservationEntity.Init` receives the concrete candidate resource IDs up front
- booking no longer depends on facility-contained resource membership inside the reservation core

This is the architectural shift the earlier handoff argued for.

### 3. Facility Is No Longer the Resource Container

Commit:

- `528953e Phase 1.3: Remove FacilityEntity as resource container`

What changed:

- `FacilityState` no longer owns `resourceIds`
- resource registration logic was removed from `FacilityEntity`
- booking-side resource lookup now uses `ResourcesByFacilityView`
- resource-to-facility association is projected from resource-side external reference data

This means facility still exists, but no longer sits at the center of booking correctness.

### 4. BookingService Was Split Into an Orchestration Chain

Commit:

- `bcee711 Phase 1.5: Split BookingService into orchestration chain`

The old `BookingService` mixed tool surface, context resolution, scope lookup, booking dispatch, and response shaping.

That was split into:

- `BookingTools` for the LLM-facing function surface
- `BookingApplicationServiceImpl` for workflow dispatch
- `BookingContextResolverAkka` for deriving booking context from the request origin
- `CourtBookingWorkflow` for court-domain booking behavior
- `CourtDirectoryAkka` for resolving facility scope to candidate resources
- `ReservationGatewayAkka` for wrapping reservation submission/cancel/get

This is the clearest implemented boundary improvement in the repo.

### 5. TelegramEndpoint Is Thinner

Commit:

- `194d626 Phase 1.6: thin TelegramEndpoint, update BookingAgent to OriginRequestContext`

`TelegramEndpoint` now:

- validates the incoming bot token via `FacilityByBotTokenView`
- derives `facilityId` and timezone
- builds an `OriginRequestContext`
- sends `AgentRequest(origin, text)` to `BookingAgent`
- returns immediately and lets notification happen asynchronously

This is still facility-aware, but the endpoint is no longer where booking orchestration itself lives.

### 6. Reservation Outcome Service Stream Added

Commit:

- `810aea6 Add ReservationOutcomeEvent and ReservationOutcomeProducer for cross-service stream`

Rez now publishes booking outcomes through the `reservation-outcomes` service stream.

Current variants:

- `Fulfilled`
- `Rejected`

And producer behavior also emits rejection on search exhaustion paths.

This is the main new integration point for other services such as `hit-backend`.

## What The Earlier Handoff Got Right

The earlier handoff was directionally correct on these points:

- Rez should become narrower around reservation and resource correctness
- booking should start from flat candidate resource IDs
- `FacilityEntity` should stop being the resource container
- `BookingService` should be treated as orchestration/application logic, not core booking logic
- Rez needs a direct API for non-AI callers

Those are no longer just proposals. They are now largely reflected in the implementation.

## What Is Still Transitional

### Facility Still Exists As A Helper Boundary

The earlier handoff assumed facility would largely leave Rez. That is only partly true today.

Facility is still used for:

- provisioning metadata
- bot-token-based Telegram routing
- timezone lookup
- calendar URL generation and facility-scoped views

So the current truth is:

- facility is no longer the booking container
- facility is still a compatibility and integration concept inside Rez

### Calendar Responsibilities Are Still Mixed

Rez currently does all of these:

- owns booking facts
- serves a read-only calendar UI
- projects reservation data into `ReservationCalendarView`
- performs Google Calendar side effects after booking fulfillment

That means the calendar boundary is still mixed between:

- canonical internal booking state
- internal read model / UI
- external mirrored side effects

The repo is in a transitional state here, not a final one.

### Google Calendar Is Still A Side Effect

The earlier handoff argued that Google Calendar should no longer be treated as the canonical mutable booking target.

That direction is still reasonable, but it has not been completed. Current behavior still includes:

- Google Calendar writes via `DelegatingServiceAction`
- user-facing messages containing calendar links

So this area should be treated as "still implemented, but architecturally open for redesign."

## Current Correctness Model

The current booking correctness model is:

- availability checks are advisory only
- a slot is locked only when `ResourceEntity.reserve()` persists `ReservationAccepted`
- the resource lock lives in `ResourceState.timeWindow`
- a positive availability reply does not lock anything
- only one concrete reserve attempt should be in flight per reservation
- `UNAVAILABLE` is a deterministic conclusion over the known candidate set

This is materially stronger than the earlier timer-driven design.

## Remaining Real Risks

The old timer race is no longer the main risk. The important remaining risk is cross-entity partial failure.

### 1. Cross-Entity Acknowledge-Then-Fail Gaps

This is still the main architectural reliability issue.

Pattern:

- a consumer/action handles an event
- it issues downstream `componentClient.invoke()` or `invokeAsync()`
- the source event may already be acknowledged
- the downstream step may fail afterward
- the workflow can be left partially advanced without automatic transactional rollback

This matters in:

- reservation to resource transitions
- resource to reservation transitions
- notification side effects
- Google Calendar side effects

This should now be treated as the primary architectural risk.

### 2. Transitional Coupling To Facility Metadata

Booking correctness no longer depends on facility-as-container, but some operational paths still depend on facility metadata living in Rez:

- Telegram validation
- timezone lookup
- provisioning flow
- facility-based resource lookup

That is acceptable for now, but it means the boundary is not fully clean yet.

### 3. Side-Effect Ordering And Compensation

Because booking fulfillment, notifications, and Google sync are decoupled, failure handling still depends on operational behavior rather than a single transaction boundary.

The internal booking lock does not depend on Google Calendar succeeding, which is good.

But operationally there is still room for:

- missed notifications
- missed downstream sync
- eventual divergence between internal booking state and external mirrors

## Provisioning Architecture: Current State

Provisioning still works, but its shape changed.

Important current behavior:

- a facility is still created through `FacilityEntity`
- a resource is created independently through `ResourceEntity`
- resource-to-facility association is then set via `ResourceEntity.setExternalRef(...)`
- `ResourcesByFacilityView` projects that relation for booking lookup

So provisioning is now effectively a two-step association model rather than facility-owned resource registration.

This is the current compatibility story:

1. Create facility.
2. Create resource.
3. Attach resource to facility via external reference.
4. Let the projection expose facility-to-resource lookup.

That is the provisioning behavior the docs and tests should reflect.

## Direct API Status

The external API requested in the earlier handoff now exists:

- `POST /bookings`
- `GET /bookings/{reservationId}`
- `DELETE /bookings/{reservationId}`

There are also recipient-based lookup endpoints for operational discovery of recent reservations.

This means Rez is already callable as a booking engine by non-AI callers that know the candidate `resourceId` set.

## Recommended Follow-Up Work

The next steps with the best payoff are:

1. Decide whether facility remains a long-lived helper concept in Rez or is moved outward more aggressively.
2. Make the cross-entity failure model more explicit with compensating behavior, retries, or a more durable orchestration strategy.
3. Decide whether Google Calendar remains a side-effect mirror, becomes a pure projection target, or moves out of Rez entirely.
4. Decide whether the HTML calendar stays embedded in Rez or becomes an external read-model application.
5. Keep expanding tests around orchestration failure cases and provisioning compatibility.

## Final Assessment

Rez is now in a better state than the earlier handoff described.

The core architectural shift has already happened:

- booking is resource-set based
- timer-driven completion is gone from the normal path
- facility is no longer the resource container
- orchestration concerns are more clearly separated from booking correctness

What remains is not a fundamental redesign of the booking core. What remains is mostly integration cleanup:

- facility compatibility boundaries
- calendar boundaries
- cross-service failure handling
- operational hardening

That is a much narrower and more concrete follow-up scope than before.
