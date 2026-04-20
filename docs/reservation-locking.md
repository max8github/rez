# How Reservation Locking Works in Rez

This document explains exactly when a reservation becomes locked on a `Resource`, based on the current implementation.

Short version:

- Rez does not lock a resource during the broadcast availability check.
- Rez locks a resource only when `ResourceEntity` persists `ReservationAccepted`.
- That event writes the rounded booking time into the resource's `timeWindow`.

The relevant code is:

- [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java)
- [ResourceState.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java)
- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java)
- [ReservationAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java)
- [ResourceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java)
- [FacilityAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityAction.java)

## The Core State

The lock lives in `ResourceState`.

`ResourceState` stores:

- `name`
- `calendarId`
- `timeWindow: Map<LocalDateTime, String>`
- `period: Period`

See:

- [ResourceState.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java:33)

`timeWindow` is the important piece. It maps a rounded booking start time to the `reservationId` that owns that slot.

Example:

```text
2026-05-01T18:00 -> "a1b2c3d4"
```

That entry is the lock.

## Step-by-Step Locking Flow

### 1. A reservation is initialized

`BookingService.bookCourt()` creates a reservation ID, starts a 14-second timer, and calls `ReservationEntity::init()`.

See:

- [BookingService.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingService.java:156)
- [RezAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/RezAction.java:17)
- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:78)

After `Inited`, the reservation enters `COLLECTING`.

### 2. Facility fan-out asks resources for availability

`ReservationAction` reacts to `Inited` and calls `FacilityAction.broadcast()`.

See:

- [ReservationAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java:25)
- [FacilityAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityAction.java:54)

For each resource, Rez sends `ResourceEntity.CheckAvailability`.

### 3. `checkAvailability()` does not lock anything

Inside `ResourceEntity::checkAvailability()`:

- the requested time is rounded to the hour
- `currentState().isReservableAt(validTime)` is evaluated
- Rez persists only `AvalabilityChecked`

See:

- [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java:67)

`isReservableAt()` returns `true` only if:

- the time is within the booking horizon
- `timeWindow` does not already contain that time key

See:

- [ResourceState.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java:48)

Important: this is only a read-like check plus an emitted event. No slot is inserted into `timeWindow` here.

### 4. First positive answer selects a candidate

`ResourceAction` forwards `AvalabilityChecked` to `ReservationEntity::replyAvailability()`.

See:

- [ResourceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java:39)

If the reservation is still in `COLLECTING` and the answer is `true`, `ReservationEntity` persists `ResourceSelected` and moves to `SELECTING`.

See:

- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:92)

This still does not lock the resource. It only means "this resource is the current candidate to attempt".

### 5. `reserve()` is the actual lock attempt

`ReservationAction` reacts to `ResourceSelected` and sends `ResourceEntity::reserve()`.

See:

- [ReservationAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java:32)

Inside `ResourceEntity::reserve()`:

- the requested time is rounded again
- `isReservableAt(validTime)` is checked again
- if still free, Rez persists `ReservationAccepted`
- if no longer free, Rez persists `ReservationRejected`

See:

- [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java:77)

This second check is essential. It closes the race window between:

- "resource looked free when checked"
- "resource is still free when we actually try to lock it"

## The Exact Moment The Lock Is Acquired

The lock is acquired when `ReservationAccepted` is applied to the `ResourceEntity` state.

That happens here:

- [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java:35)

On `ReservationAccepted`, the entity executes:

```java
currentState().set(ResourceState.roundToValidTime(e.reservation().dateTime()), e.reservationId())
```

That inserts:

- key: rounded booking start time
- value: reservation ID

into `timeWindow`.

From then on:

- `timeWindow.containsKey(validTime)` becomes `true`
- `isReservableAt(validTime)` becomes `false`
- later availability checks and reserve attempts for the same slot on the same resource will fail

## What "Locked" Means in Practice

In current Rez, a slot is locked if and only if all of these are true:

- the relevant `ResourceEntity` has persisted `ReservationAccepted`
- the corresponding event has been applied to state
- `ResourceState.timeWindow` contains the rounded time mapped to the reservation ID

It is not locked when:

- the reservation exists in `ReservationEntity`
- availability was checked
- the resource answered `available=true`
- the reservation entered `SELECTING`

Those are all pre-lock stages.

## Time Normalization

Rez normalizes the requested time before both checking and locking:

- minutes are removed
- seconds are removed
- nanos are removed

See:

- [ResourceState.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java:71)

So:

- `2026-05-01T18:07:12` is treated as `2026-05-01T18:00:00`

That means the lock is hourly, not minute-precise.

## What Happens If Two Reservations Race For The Same Resource

This is the important concurrency story.

Assume reservations `R1` and `R2` both target the same resource and time.

1. Both may receive `available=true` during the fan-out stage if they check before either one has locked.
2. Both may therefore move toward selecting that same resource as a candidate.
3. Both may call `ResourceEntity::reserve()`.
4. Because the resource entity owns the slot state, reserve attempts are serialized through that entity's event-sourced command handling.
5. The first reserve that persists `ReservationAccepted` writes the slot into `timeWindow`.
6. The later reserve sees the slot already present and persists `ReservationRejected`.

This is why the actual correctness point is the second check in `reserve()`, not the first check in `checkAvailability()`.

## What Happens After The Lock

After `ReservationAccepted`:

- `ResourceAction` reacts to the event
- it calls `ReservationEntity::fulfill()`
- `ReservationEntity` persists `Fulfilled`
- the timer is deleted
- `DelegatingServiceAction` mirrors the booking to Google Calendar and sends the user notification

See:

- [ResourceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java:50)
- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:128)
- [DelegatingServiceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/DelegatingServiceAction.java:33)

Important: Google Calendar write is after the internal lock. The lock does not depend on Google succeeding.

## What Happens If The Candidate Rejects

If the selected resource rejects during `reserve()`:

- `ResourceEntity` persists `ReservationRejected`
- `ResourceAction` calls `ReservationEntity::reject()`
- if `availableResources` already contains another candidate, Rez emits `RejectedWithNext`
- `ReservationAction` feeds that next candidate back into `replyAvailability(..., true)`
- the reservation tries the next available resource

See:

- [ResourceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java:69)
- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:195)

So a rejected candidate does not fail the whole reservation immediately. Rez can fall back to other already-known available resources.

## How The Lock Is Released

The lock is released during cancellation.

Flow:

1. `ReservationEntity::cancelRequest()` persists `CancelRequested`
2. `ReservationAction` calls `ResourceEntity::cancel(reservationId, dateTime)`
3. `ResourceEntity` persists `ReservationCanceled`
4. applying that event calls `currentState().cancel(dateTime, reservationId)`
5. `ResourceState.cancel()` removes the `(time, reservationId)` pair if it matches exactly

See:

- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:144)
- [ReservationAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java:55)
- [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java:95)
- [ResourceState.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java:56)

Two details matter here:

- cancellation also rounds the time before removing it
- the slot is removed only if the stored reservation ID matches the cancelling reservation ID

That prevents one reservation from deleting another reservation's lock for the same hour.

## What The Timer Does And Does Not Do

Rez starts a 14-second timer when the reservation is created.

See:

- [BookingService.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingService.java:171)
- [RezAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/RezAction.java:20)

If the reservation is not fulfilled in time:

- `TimerAction::expire()` calls `ReservationEntity::expire()`
- the reservation becomes `UNAVAILABLE`

See:

- [TimerAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/TimerAction.java:19)
- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java:182)

The timer does not release a resource lock, because no lock exists unless a resource already accepted. Once a resource accepts and fulfillment completes, the timer is deleted.

## Precise Answer

If the question is:

"Exactly how is a reservation locked by a Resource?"

The answer is:

1. The reservation asks many resources whether they are free.
2. A positive answer does not lock the slot.
3. The reservation picks one candidate and sends `reserve()` to that `ResourceEntity`.
4. That resource checks the same rounded time again against its own `timeWindow`.
5. If still free, it persists `ReservationAccepted`.
6. Applying `ReservationAccepted` inserts `(roundedDateTime -> reservationId)` into `ResourceState.timeWindow`.
7. That inserted map entry is the lock.

Everything before step 6 is only selection and contention handling.
