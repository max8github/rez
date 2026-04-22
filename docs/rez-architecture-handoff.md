# Rez Architecture Handoff

Date: 2026-04-20

This document consolidates the current analysis and the architectural changes that should be implemented in follow-up work.

Status note as of 2026-04-22:

- this document is now partly historical
- the timer-driven reservation completion flow described below is no longer current implementation
- Rez now completes normal booking search by exhausting the known candidate `resourceId` set rather than by business timeout
- the external `/bookings` API now exists

Use this document mainly for architectural rationale and long-term direction, not as an exact description of current code paths.

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

Historical note:

- the timer/lock inconsistency described in this section was the urgent issue at the time of writing
- that exact timer-driven flow is no longer the current implementation after the timer-removal refactor
- the remaining important risk is still cross-entity partial failure / compensation, not timeout-driven completion

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

Implemented direction:

- if a `ResourceEntity` has accepted and locked a slot
- but `ReservationEntity` can no longer fulfill
- then Rez must immediately release the resource lock by issuing compensating cancellation to the resource

### Timer Semantics Revisit

This section is now historical.

Current code no longer uses business timeout as the normal mechanism that decides `UNAVAILABLE`.

At the time of writing, timeout behavior was too coarse because `expire()` could move a reservation to `UNAVAILABLE` from:

- `INIT`
- `COLLECTING`
- `SELECTING`

That is suspicious from a state-machine perspective. `SELECTING` already means a candidate resource has been chosen and an actual reserve attempt is in progress or about to be in progress.

That specific follow-up has effectively been superseded by the timer-removal refactor.

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

Status:

- implemented in principle via `/bookings`
- additional operational lookup endpoints now also exist for recipient-based reservation discovery

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

## 9. Target Service Landscape

This section describes the intended multi-service direction using the actual app/service names discussed so far.

### Rez

Rez should become a narrow booking engine.

Owns:

- reservation orchestration
- resource locking
- cancellation / release
- booking timeout behavior
- booking policies tied directly to booking correctness
- bookable resource state

Does not own:

- club/facility rich metadata
- supplier/player rich metadata
- radius/location search
- ranking/rating logic
- user-facing conversational orchestration
- rich calendar UI

### Hit

Hit is the app for finding coaches / supplier-like player resources.

Known parts:

- `hit-backend`
- `hit-app`

Responsibilities:

- geolocation/radius search for candidate suppliers/players
- supplier/player discovery
- organized tennis session creation among players
- Stripe account onboarding / related supplier payment setup
- Hit-specific player level/rating logic

Important clarification:

- Hit rating is not identical to Rank rating.
- Hit has its own concept of level/rating.
- Hit can use many credentials, including federation information and other sources, and translate those into a single Hit rating number.
- Rank match results may contribute to Hit rating in the future, but Hit rating and Rank rating are not currently the same domain concept.

Rez should be called by Hit for booking supplier resources, but Hit should remain owner of supplier discovery and session/business logic.

### Rank / Rakkar

Rank is the player ranking app. The codebase currently uses the name `rAKKAr` / `Rakkar`.

Codebase:

- `/Users/max/code/elo-manager`

Backend services:

- `match-service`
- `player-service`

Responsibilities:

- player management in the ranking context
- match submission and match history
- rating calculation
- leaderboard / ranking views

Important clarification:

- Rank rating and Hit rating are separate concepts today, even if they share the same rough numeric range.
- It is still important to know which Hit player corresponds to which Rank player.
- Rank should stay a separate bounded context from Rez.

### Clubs / Facilities Service

This service does not yet exist as finalized architecture, but is the natural place for:

- clubs / facilities
- courts as business objects
- addresses / locations
- club metadata
- provisioning of facilities and courts
- possibly membership/admin associations

This service would be the owner of facility/court catalog information, while Rez would only own booking state for the corresponding resources.

### Booking Orchestration Service

This is the place where the current `BookingService` concept belongs, even if it remains in the Rez repo or deployable for now.

Responsibilities:

- Telegram integration
- AI orchestration
- gathering metadata from other services
- calling Rez as a booking tool
- enriching booking results for human responses

This should be treated conceptually as outside the Rez core.

### Calendar View Service

This should likely become a dedicated view/read-model service rather than remain embedded in Rez long-term.

Responsibilities:

- rendering calendar UI / HTML
- serving reservation/resource calendar views
- optionally enriching calendar display with metadata from external services

Rez should remain the source of booking facts, while the calendar layer should act as a view over that data.

## 10. Player / Identity Boundary Notes

### Minimal Player Data Principle

Player information should remain as minimal as possible to reduce privacy/data-protection burden.

The intended minimal data footprint is roughly:

- email
- sport age category (`U12`, `U14`, `U16`, ...)
- gender categorization

Private/sensitive data should be offloaded whenever possible to external platforms such as:

- Google
- Apple
- Stripe

### Consequence

Avoid creating a large generic "user service" too early.

Instead:

- keep domain-specific ownership in the relevant apps/services
- keep shared identity surface minimal
- use references/mappings where needed

### Important Cross-Service Identity Need

Even if Hit and Rank maintain separate bounded-context meanings of player data, it remains important to know:

- which Hit player corresponds to which Rank player

That mapping should be treated as an explicit cross-service identity concern.

This does not imply that Hit and Rank must share the same rating model, only that they need a reliable way to connect the same human/person across contexts when appropriate.

## 11. Supplier Use Case

The supplier use case is a strong argument for narrowing Rez:

- supplier service already performs location/radius filtering
- supplier service can produce a set of candidate supplier `resourceId`s
- supplier service can call Rez directly
- AI is optional, not required

This reinforces the direction:

- supplier service does discovery
- Rez does booking competition and locking
- external orchestration enriches the booking result

## 12. Recommended Implementation Order

### Phase 1: Correctness First

Status:

- immediate compensation was implemented
- timer-driven completion was removed from the normal booking flow
- tests were added around deterministic exhaustion / rejection behavior

### Phase 2: Narrow Rez Booking API

1. Design and add an external API that starts reservations from flat `resourceId` sets.
2. Keep existing BookingService path working while introducing the direct API.

Status:

- largely implemented

### Phase 3: Resource Model Expansion

1. Add booking window policy fields to resource state/model.
2. Add external reference fields.
3. Add optional external group/container reference if needed.

### Phase 4: Remove Facility From Core Booking

1. Stop making reservation flow depend on facility as a first-class booking entity.
2. Reassess whether `FacilityEntity` remains as a temporary compatibility helper or is removed entirely.
3. Remove `SelectionItem` if reservation input becomes resource-only.

Status:

- core reservation initiation now starts from flat `resourceId` sets
- facility still remains in the codebase as a compatibility/helper concept for provisioning, Telegram routing, and some calendar/UI paths

### Phase 5: Rework Integrations

1. Redesign Telegram orchestration around external resource selection/group resolution.
2. Redesign calendar responsibilities and decide what remains in Rez versus moves out.
3. Reassess Google Calendar as import/projection instead of booking side effect.

## 13. Open Questions

These remain open and should be answered during implementation planning:

- Should Rez temporarily keep a thin facility helper model during migration, or remove facility from booking immediately?
- What is the exact external API shape for resource-set-based reservation?
- Should result enrichment happen synchronously in the caller or asynchronously through event consumption?
- What minimal metadata, if any, should Rez still cache locally for display and notification purposes?
- Should the HTML calendar move out of Rez immediately, or later?
- What is the migration path for Telegram routing and provisioning once facility leaves Rez?
- What is the minimal but reliable shared identity/mapping strategy between Hit players and Rank players?

## 14. Final Intended Direction

The intended architecture after these changes is:

- Rez = booking engine
- Hit = coach/supplier discovery and session domain
- Rank / Rakkar = ranking and match domain
- clubs/facilities service = facility/court catalog domain
- external orchestration services = AI, Telegram, metadata enrichment, UI/channel integration
- BookingService = application/orchestration layer, conceptually outside Rez
- reservation input = flat set of candidate resource IDs
- resource = unified bookable abstraction with richer policy and external references

That keeps Rez focused on the one thing it must do correctly:

- decide and persist who got booked, and lock the resource consistently
