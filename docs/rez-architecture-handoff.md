# Rez Architecture Handoff

Date: 2026-04-20

This document consolidates the current analysis and the architectural changes that should be implemented in follow-up work.

The goal is to make Rez a narrower booking engine, reduce domain leakage from other bounded contexts, and fix correctness issues in the current booking flow.

## Executive Summary

The main conclusions are:

- The most urgent issue is a timer race that can leave a `Resource` locked while the corresponding `Reservation` has failed as `UNAVAILABLE`.
- Rez should move toward a narrower core centered on `Reservation` and `Resource`.
- `Facility` should no longer be a first-class booking domain object inside Rez.
- `SelectionItem` likely becomes unnecessary if Rez always books from a flat list of `resourceId`s.
- `ResourceState` should be extended with booking-policy and external-reference fields.
- `BookingService` can remain in-repo for now, but should be treated conceptually as outside the Rez core.
- Rez needs a first-class external API for non-AI callers.
- Telegram and calendar responsibilities need to be re-evaluated in light of the narrower Rez boundary.

## 1. Urgent Correctness Fix

### Problem

There is a real race between:

- resource acceptance and locking
- reservation timeout expiry

Current problematic flow:

1. Reservation is close to timeout.
2. `ReservationAction` sends `ResourceEntity::reserve()`.
3. `ResourceEntity` persists `ReservationAccepted`, which locks the slot in `ResourceState.timeWindow`.
4. Before reservation-side fulfillment completes, the timer fires.
5. `ReservationEntity::expire()` persists `SearchExhausted`, transitioning the reservation to `UNAVAILABLE`.
6. `ResourceAction` later calls `ReservationEntity::fulfill()`.
7. `fulfill()` no longer persists `Fulfilled` because the reservation is not in `SELECTING`.

Result:

- resource is booked
- reservation is failed

This is not acceptable.

Relevant code:

- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java)
- [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java)
- [ResourceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java)
- [TimerAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/TimerAction.java)

### Required Immediate Mitigation

Implement compensation:

- if a `ResourceEntity` has accepted and locked a slot
- but `ReservationEntity` can no longer fulfill
- then Rez must immediately release the resource lock by issuing compensating cancellation to the resource

This is the most urgent change and should be implemented first.

### Timer Semantics Revisit

Current timeout behavior is too coarse because `expire()` can move a reservation to `UNAVAILABLE` from:

- `INIT`
- `COLLECTING`
- `SELECTING`

That is suspicious from a state-machine perspective. `SELECTING` already means a candidate resource has been chosen and an actual reserve attempt is in progress or about to be in progress.

Recommended follow-up:

- re-evaluate timeout behavior for `SELECTING`
- likely do not allow `expire()` to finalize `UNAVAILABLE` from `SELECTING`
- handle `SELECTING` with different watchdog semantics than pure search

### Other Race / Reliability Issue To Document

There is another important race/reliability concern, distinct from the timer race:

- consumers/actions use downstream `componentClient.invoke()` or `invokeAsync()` and then return `effects().done()`
- if the downstream call fails after the consumer has acknowledged the event, the event will not be retried automatically

This means the orchestration is not transactionally atomic across entities and can fail in partially applied ways.

This is already noted in:

- [docs/DEVELOPMENT_PLAN.md](/Users/max/code/rez/docs/DEVELOPMENT_PLAN.md:55)

It should be treated as a real architectural risk in the booking flow, especially around:

- reservation -> resource transitions
- resource -> reservation transitions
- side effects such as notifications or calendar sync

### Existing Late-Rejection Race

There is also a known late-rejection race that the code already partially handles:

- a `ReservationRejected` may arrive after the reservation is already completed
- current code ignores this rather than failing the consumer

See:

- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:211)

That race is already mitigated and is less urgent than the timer/lock inconsistency.

## 2. Facility Should Leave Rez

### Decision

`Facility` should no longer be a first-class booking domain object inside Rez.

Reasoning:

- A facility is fundamentally a grouping/container concept.
- The rich meaning of facility belongs in another bounded context.
- Rez does not need facility metadata to do booking correctness.
- External selection logic already exists and can resolve a facility to a set of `resourceId`s.

### Implication

Rez should move toward a model where booking starts from a flat set of resource IDs, not from facility IDs.

That means the true core booking model becomes:

- `Reservation`
- `Resource`

not:

- `Reservation`
- `Facility`
- `Resource`

### Current Consequences To Revisit

The current code still assumes facility context in a few places:

- timezone lookup
- Telegram routing
- calendar address/labeling
- provisioning

Those concerns need to be moved outward or reworked.

## 3. SelectionItem Likely Goes Away

### Current Role

`SelectionItem` currently exists to allow a reservation selection to contain:

- `FACILITY`
- `RESOURCE`

This was useful when Rez wanted to support mixed selections and recursive containment.

### New Direction

If Rez no longer models facility as a first-class booking entity and reservations always start from resource IDs, then `SelectionItem` is likely unnecessary.

Reservation input would then become conceptually:

- reservation details
- `Set<String> resourceIds`

instead of:

- reservation details
- `Set<SelectionItem>`

### Caveat

If future booking inputs truly need heterogeneous target types again, this should be reconsidered. But for the current direction, `SelectionItem` looks like leftover generality.

## 4. ResourceState Should Grow New Fields

### Why

A unified resource abstraction still makes sense in Rez:

- tennis court
- conference room
- supplier/person
- teacher
- hotel room

All of these are still bookable resources at the booking-engine level.

What varies is usually:

- availability policy
- metadata
- external identity

not the basic lock/release lifecycle.

### New Dimensions For ResourceState

The resource model should be expanded to explicitly represent:

#### 1. Occupancy

This already exists:

- booked slots / lock ownership in `timeWindow`

#### 2. Booking Window Policy

Needed for:

- supplier bookable only in afternoons
- specific weekdays only
- no night bookings
- maintenance periods
- seasonal or temporary blackout periods

This is generic and useful for all resource kinds, not just suppliers.

#### 3. External Reference

Needed to point to the canonical record in another bounded context.

Examples:

- supplier person/entity ID in another service
- court asset/entity ID in another service

Rez should own booking state, not all descriptive metadata.

#### 4. Optional External Group/Container Reference

Facility may still matter as an external grouping reference, even if not as a Rez entity.

This could be used for:

- reverse lookup
- reporting
- UI enrichment
- context resolution

But it should be treated as an external reference, not a Rez-owned container entity.

#### 5. Minimal Booking-Relevant Display Metadata

Only if operationally needed.

Examples:

- display label
- timezone

Canonical descriptive metadata should still live outside Rez.

### Important Boundary

Do not immediately split into `SupplierResourceEntity` unless behavior truly diverges at the state-machine level.

For now, one generalized `ResourceEntity` with richer policy and metadata is the preferred direction.

## 5. BookingService Boundary

### Decision

`BookingService` should be treated conceptually as outside Rez, even if it stays in the repo and deployable unit for now.

### Why

Rez should become the booking engine.

`BookingService` is an orchestration/application layer that may:

- interpret natural language
- resolve user intent
- gather metadata
- call Rez
- enrich Rez results
- send human-facing responses

That is not the same responsibility as booking consistency and resource locking.

### Practical Conclusion

- no need to extract `BookingService` into a separate deployable service immediately
- but do not let its current in-repo placement define the conceptual architecture

## 6. Rez Needs an External API

### Why

Rez should be callable directly by services other than the current AI/Telegram flow.

This is especially needed for supplier booking use cases where:

- another service already knows the candidate resources
- another service may not need AI at all
- Rez should simply be called to reserve from a set of resource IDs

### Target Direction

Rez should expose a first-class API for:

- create reservation from a flat set of `resourceId`s
- check status
- cancel reservation
- query booking result

The result should be minimal and booking-centric:

- reservation ID
- chosen resource ID
- locked time
- booking status

Metadata enrichment can happen outside Rez.

## 7. Calendar Responsibilities Need Rework

### Rez HTML Calendar

Rez currently includes an HTML calendar / calendar view capability.

That should be re-evaluated.

If Rez becomes a narrower booking engine, then a richer calendar UI may belong outside Rez as a separate consumer/view layer over Rez booking data.

Possible direction:

- Rez emits or projects booking state
- another service/app hosts the calendar UI

### Google Calendar Side Effect

Current Google Calendar side-effecting is likely no longer the desired model.

Desired direction:

- Google Calendar should not be the mutable side-effect target of booking completion
- the canonical calendar should be a view/projection of resource reservation data

However, Google Calendar may still be relevant for:

- import into Rez
- synchronization or ingestion
- one-way projection if explicitly needed later

This needs to be redesigned explicitly rather than assumed from the current implementation.

## 8. Telegram Responsibilities Need Rework

### Current Situation

Telegram currently acts as the frontend of Rez through `BookingService`.

It relies on facility-oriented routing and provisioning concepts.

### Problem

If facility leaves Rez as a first-class entity:

- Telegram can no longer rely on Rez-local facility semantics in the same way
- provisioning flow changes
- Rez will not inherently know facility metadata or facility membership

### Open Design Question

For Telegram to keep working, one of these needs to happen:

1. Telegram-facing orchestration resolves the facility to a resource set before calling Rez
2. Telegram-facing orchestration calls another service to resolve facility metadata and resources
3. Rez keeps a thin helper/reference model for facility routing only, without making facility core to booking

This needs to be fleshed out before implementation.

### Important Point

Telegram support should keep working, but the Telegram layer should not force Rez to retain domain concepts that properly belong elsewhere.

## 9. Supplier Use Case

The supplier use case is a strong argument for narrowing Rez:

- supplier service already performs location/radius filtering
- supplier service can produce a set of candidate supplier `resourceId`s
- supplier service can call Rez directly
- AI is optional, not required

This reinforces the direction:

- supplier service does discovery
- Rez does booking competition and locking
- external orchestration enriches the booking result

## 10. Recommended Implementation Order

### Phase 1: Correctness First

1. Implement immediate compensation if resource accepted but reservation cannot fulfill.
2. Rework timeout semantics around `SELECTING`.
3. Add tests specifically for timer/acceptance race conditions.

### Phase 2: Narrow Rez Booking API

1. Design and add an external API that starts reservations from flat `resourceId` sets.
2. Keep existing BookingService path working while introducing the direct API.

### Phase 3: Resource Model Expansion

1. Add booking window policy fields to resource state/model.
2. Add external reference fields.
3. Add optional external group/container reference if needed.

### Phase 4: Remove Facility From Core Booking

1. Stop making reservation flow depend on facility as a first-class booking entity.
2. Reassess whether `FacilityEntity` remains as a temporary compatibility helper or is removed entirely.
3. Remove `SelectionItem` if reservation input becomes resource-only.

### Phase 5: Rework Integrations

1. Redesign Telegram orchestration around external resource selection/group resolution.
2. Redesign calendar responsibilities and decide what remains in Rez versus moves out.
3. Reassess Google Calendar as import/projection instead of booking side effect.

## 11. Open Questions

These remain open and should be answered during implementation planning:

- Should Rez temporarily keep a thin facility helper model during migration, or remove facility from booking immediately?
- What is the exact external API shape for resource-set-based reservation?
- Should result enrichment happen synchronously in the caller or asynchronously through event consumption?
- What minimal metadata, if any, should Rez still cache locally for display and notification purposes?
- Should the HTML calendar move out of Rez immediately, or later?
- What is the migration path for Telegram routing and provisioning once facility leaves Rez?

## 12. Final Intended Direction

The intended architecture after these changes is:

- Rez = booking engine
- external services = discovery, metadata, orchestration, UI/channel integration
- BookingService = application/orchestration layer, conceptually outside Rez
- reservation input = flat set of candidate resource IDs
- resource = unified bookable abstraction with richer policy and external references

That keeps Rez focused on the one thing it must do correctly:

- decide and persist who got booked, and lock the resource consistently
