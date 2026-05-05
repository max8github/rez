# Notion Export Relevance for Current Rez

Source reviewed: [ExportBlock-Part-1/Reservation System 4c473379277246ac992e680a2e2ef19d.md](/Users/max/code/rez/docs/ExportBlock-Part-1/Reservation%20System%204c473379277246ac992e680a2e2ef19d.md)

This note extracts what is still relevant in the old Notion export and contrasts it with the Rez code and current docs as of 2026-04-20.

## Executive Summary

The old document still contains the core idea that matters most today:

- a `Facility` is a container of bookable `Resource`s
- a reservation request starts from a higher-level selection and fans out to candidate resources
- resources first answer availability, then one candidate is asked to actually reserve
- cancellation is driven from the reservation side and clears the resource slot
- Google Calendar is a side-effect view, not the source of truth

The obsolete parts are mostly about:

- `Kalix` terminology and old endpoint shapes
- Akka Serverless/FaaS questions
- Slack/Twist/Twilio exploratory notes
- older sequential-search designs that are no longer how Rez works
- workflows that are not the current implementation
- a circular-array slot model for resources, which is not the current state model

The current best technical description of booking flow is still
[reservation/reservation/docs/booking-flow.md](/Users/max/code/rez/reservation/reservation/docs/booking-flow.md),
but the old export is still useful because it explains the intent behind the two-phase process:
availability check first, actual lock second.

## What Is Still Relevant

### 1. Facility and Resource modeling

The export says a facility contains resources and booking typically starts from the facility rather than a specific resource. That is still true in the current implementation:

- `FacilityEntity` stores the registered resource IDs and delegates availability checks for them
  [FacilityEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityEntity.java:109)
- `FacilityAction.broadcast()` fans out the request to either facilities or resources
  [FacilityAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityAction.java:54)
- `BookingService.bookCourt()` initializes the reservation with a facility selection, not a direct resource selection
  [BookingService.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingService.java:167)

This matches the old export's description around lines 481-507.

### 2. Broadcast availability, then reserve one candidate

This is the most valuable part of the old document and it is still materially correct.

Old export:

- facility asks all resources if they are available
- first positive answer becomes the candidate
- then the chosen resource is asked to block/book

Current Rez:

- `ReservationEntity::init` persists `Inited`
  [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:78)
- `ReservationAction` reacts to `Inited` and calls `FacilityAction.broadcast()`
  [ReservationAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java:25)
- each `ResourceEntity` receives `checkAvailability()`
  [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java:67)
- each availability reply is sent back to `ReservationEntity`
  [ResourceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java:39)
- the first `true` reply received while the reservation is in `COLLECTING` persists `ResourceSelected`
  [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:92)
- only after that does `ReservationAction` call `ResourceEntity::reserve`
  [ReservationAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java:32)

This is the exact modern form of the old idea.

### 3. Reservation-centered orchestration

The old export gradually moves from "facility as aggregate root" toward "reservation entity orchestrates the booking process". The second idea is what Rez does now.

Current Rez uses:

- `ReservationEntity` as the lifecycle owner of the booking request
- `ResourceEntity` as the owner of per-resource slot occupancy
- consumers/actions to wire entity events together asynchronously

That later part of the old export, especially around lines 815-837, is closer to current reality than the earlier facility-centric wording.

### 4. Cancellation flow

The old export's cancellation section is still substantially correct:

- cancellation starts from `ReservationEntity`
- the resource slot is cleared
- reservation state transitions afterward
- external side effects happen after the state transition

Current code:

- `ReservationEntity::cancelRequest()`
  [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:144)
- `ReservationAction` calls `ResourceEntity::cancel()`
  [ReservationAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java:55)
- `ResourceEntity` persists `ReservationCanceled`
  [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java:95)
- `ResourceAction` calls `ReservationEntity::cancel()`
  [ResourceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java:81)
- `DelegatingServiceAction` removes the Google event and notifies the user
  [DelegatingServiceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/DelegatingServiceAction.java:98)

### 5. External calendar as projection / side effect

The export repeatedly questions whether Google Calendar should be the source of truth. Current Rez answers that clearly: it should not.

This is already reflected in:

- [docs/DEVELOPMENT_PLAN.md](/Users/max/code/rez/docs/DEVELOPMENT_PLAN.md:43)
- [DelegatingServiceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/DelegatingServiceAction.java:33)

The reservation and resource entities are the source of truth. Google Calendar is a mirror written after fulfillment.

## What Is Obsolete

### 1. Kalix / Akka Serverless framing

Anything describing Kalix annotations, old HTTP endpoint shapes, or Akka Serverless deployment questions is historical only. Rez now uses Akka SDK 3.x.

See:

- [docs/DEVELOPMENT_PLAN.md](/Users/max/code/rez/docs/DEVELOPMENT_PLAN.md:76)

### 2. Messaging-provider exploration

The Twilio, Slack, WhatsApp, Twist research in the export is not useful as system documentation now. It is product exploration, not current behavior.

### 3. Circular-array slot model

The export describes a resource as a circular array of slots. That is not how current Rez models occupancy.

Current `ResourceState` uses:

- a `Map<LocalDateTime, String>` called `timeWindow`
- a booking horizon of `Period.ofMonths(3)`
- hourly rounding with `roundToValidTime()`

See:

- [ResourceState.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java:33)

### 4. Sequential run-search as the main algorithm

Some sections of the export describe a reservation repeatedly selecting from a prebuilt list in a more sequential search loop. Current Rez does not start that way. It starts with a broadcast fan-out and accumulates positive responders dynamically.

The current mechanism is:

- broadcast availability checks
- collect positive responders in `availableResources`
- pick the first `true` reply that arrives while in `COLLECTING`
- if reserve fails, retry from the already collected set

See:

- [ReservationState.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationState.java:9)
- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:195)

### 5. Workflow-based cancellation

The export mentions a workflow-based variant. That is not the current path. Current cancellation is done with entity events plus consumers/actions, not a workflow.

## Important Clarification: How Locking Really Works Now

The old document talks about "block it" and "confirm it". That is still conceptually correct, but the exact current behavior is:

1. Availability check does not lock anything.
2. A positive availability reply only marks a candidate in `ReservationEntity`.
3. The actual lock happens only when `ResourceEntity::reserve()` persists `ReservationAccepted`.
4. Applying `ReservationAccepted` writes the rounded time slot into `ResourceState.timeWindow`.
5. From that point on, `isReservableAt()` returns `false` for that resource and time.
6. Cancellation removes that exact `(rounded time, reservationId)` entry.

That is documented in more detail in
[reservation-locking.md](/Users/max/code/rez/docs/reservation-locking.md).

## Where The Old Export Describes Current Functionality Better

The export is better than current docs in a few places:

- It makes the two-step idea explicit:
  "availability answer" is distinct from "actual block/book".
- It explains the mental model of a booking request entering at a higher-level container and being delegated downward.
- It explains why the reservation process exists as its own coordination object, rather than booking directly in the facility.

Current docs are better at:

- the actual implemented component names
- the Akka SDK 3.x event flow
- the Telegram/agent entry path
- the current external side effects

## Recommended Documentation Baseline

For current Rez documentation, the best combination is:

- keep [reservation/reservation/docs/booking-flow.md](/Users/max/code/rez/reservation/reservation/docs/booking-flow.md) as the main end-to-end user flow
- use [reservation-locking.md](/Users/max/code/rez/docs/reservation-locking.md) as the precise explanation of locking and race handling
- use this note only as an audit of what survived from the old export

## Gaps And Risks In Current Behavior

A few implementation details are worth noting because they affect how "locking" should be understood:

- Availability replies are asynchronous and can arrive after the reservation has already moved to `SELECTING` or `FULFILLED`. Those late replies are still persisted as `AvailabilityReplied`, but they do not re-open selection.
- The reservation does not reserve multiple resources optimistically. Only one candidate at a time receives `reserve()`.
- The timer is cancelled after `ReservationAccepted` triggers fulfillment, not at availability time.
- Consumers use fire-and-forget style downstream invocations in several places, so lock orchestration is eventually consistent rather than transactionally atomic across entities.

Those are not necessarily bugs, but they matter when reasoning about concurrent bookings.
