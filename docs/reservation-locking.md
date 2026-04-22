# How Reservation Locking Works in Rez

This document explains when a reservation becomes locked on a `Resource`, based on the current implementation after the timer-removal refactor.

Short version:

- Rez does not lock a resource during the availability fan-out.
- Rez locks a resource only when `ResourceEntity` persists `ReservationAccepted`.
- Search completion is deterministic from the known candidate `resourceId` set. A business timeout is no longer part of normal booking completion.

The relevant code is:

- [ResourceEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java)
- [ResourceState.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java)
- [ReservationEntity.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java)
- [ReservationAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java)
- [ResourceAction.java](/Users/max/code/rez/reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java)

## The Core Lock State

The lock lives in `ResourceState.timeWindow`.

`ResourceState` stores:

- `name`
- `calendarId`
- `timeWindow: Map<LocalDateTime, String>`
- `period: Period`
- optional booking policy / metadata fields

`timeWindow` maps a rounded booking start time to the `reservationId` that owns that slot.

Example:

```text
2026-05-01T18:00 -> "a1b2c3d4"
```

That map entry is the lock.

## Step-by-Step Locking Flow

### 1. A reservation is initialized

The caller creates a reservation and passes the full candidate `resourceId` set up front.

Examples:

- `BookingService.bookCourt(...)`
- `POST /bookings`

`ReservationEntity::init()` persists `Inited` and moves the reservation into the search phase.

Important detail:

- the reservation already knows the full candidate set at initialization time
- Rez no longer depends on a timer to conclude that a reservation is `UNAVAILABLE`

### 2. Availability is fanned out to all known candidate resources

`ReservationAction` reacts to `Inited` and sends `ResourceEntity.CheckAvailability` to each candidate resource.

This is an advisory check only. It does not lock anything.

### 3. `checkAvailability()` does not lock anything

Inside `ResourceEntity::checkAvailability()`:

- the requested time is rounded to the valid booking slot
- `currentState().isReservableAt(validTime)` is evaluated
- Rez persists only `AvalabilityChecked`

No slot is inserted into `timeWindow` here.

### 4. A positive answer may select a candidate

`ResourceAction` forwards `AvalabilityChecked` to `ReservationEntity::replyAvailability()`.

If the reservation is not currently reserving another candidate and the answer is positive, the reservation persists `ResourceSelected` and moves into the reserve-in-flight phase.

This still does not lock the resource. It only means:

- "this resource is the current candidate to attempt"

### 5. `reserve()` is the actual lock attempt

`ReservationAction` reacts to `ResourceSelected` and sends `ResourceEntity::reserve()`.

Inside `ResourceEntity::reserve()`:

- the requested time is rounded again
- `isReservableAt(validTime)` is checked again
- if still free, Rez persists `ReservationAccepted`
- if no longer free, Rez persists `ReservationRejected`

This second check is the real correctness point. It closes the race window between:

- "resource looked free when checked"
- "resource is still free when we actually try to lock it"

## The Exact Moment the Lock Is Acquired

The lock is acquired when `ReservationAccepted` is applied to the `ResourceEntity` state.

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
- later availability checks and reserve attempts for the same slot on the same resource fail

## What "Locked" Means in Practice

In current Rez, a slot is locked if and only if all of these are true:

- the relevant `ResourceEntity` has persisted `ReservationAccepted`
- the corresponding event has been applied to state
- `ResourceState.timeWindow` contains the rounded time mapped to the reservation ID

It is not locked when:

- the reservation exists in `ReservationEntity`
- availability was checked
- the resource answered `available=true`
- the reservation selected a candidate but the `reserve()` call has not yet accepted

Those are all pre-lock stages.

## Time Normalization

Rez normalizes the requested time before both checking and locking:

- minutes are removed
- seconds are removed
- nanos are removed

So:

- `2026-05-01T18:07:12` is treated as `2026-05-01T18:00:00`

That means the lock is hourly, not minute-precise.

## What Happens If Two Reservations Race For the Same Resource

Assume reservations `R1` and `R2` both target the same resource and time.

1. Both may receive `available=true` during the fan-out stage if they check before either one has locked.
2. Both may therefore move toward selecting that same resource as a candidate.
3. Both may call `ResourceEntity::reserve()`.
4. Because the resource entity owns the slot state, reserve attempts are serialized through that entity's event-sourced command handling.
5. The first reserve that persists `ReservationAccepted` writes the slot into `timeWindow`.
6. The later reserve sees the slot already present and persists `ReservationRejected`.

This is why the actual correctness point is the second check in `reserve()`, not the first check in `checkAvailability()`.

## What Happens After the Lock

After `ReservationAccepted`:

- `ResourceAction` reacts to the event
- it calls `ReservationEntity::fulfill()`
- `ReservationEntity` persists `Fulfilled`
- `DelegatingServiceAction` later handles notification/calendar side effects

Important:

- external side effects happen after the internal lock
- the resource lock does not depend on Google Calendar succeeding

## What Happens If the Candidate Rejects

If the selected resource rejects during `reserve()`:

- `ResourceEntity` persists `ReservationRejected`
- `ResourceAction` calls `ReservationEntity::reject()`
- if another already-known available candidate exists, the reservation selects it immediately
- otherwise, if availability replies are still pending, the reservation goes back to waiting
- if there are no available candidates and no pending replies left, the reservation persists `SearchExhausted` and becomes `UNAVAILABLE`

That means `UNAVAILABLE` is now determined by candidate exhaustion, not by timeout.

## How the Lock Is Released

The lock is released during cancellation or compensation.

Normal cancellation flow:

1. `ReservationEntity::cancelRequest()` persists `CancelRequested`
2. `ReservationAction` calls `ResourceEntity::cancel(reservationId, dateTime)`
3. `ResourceEntity` persists `ReservationCanceled`
4. applying that event calls `currentState().cancel(dateTime, reservationId)`
5. `ResourceState.cancel()` removes the `(time, reservationId)` pair if it matches exactly

Compensation flow:

- if a resource has already accepted and locked
- but the reservation can no longer fulfill
- `ResourceAction` compensates by issuing `ResourceEntity::cancel(...)`

Two details matter here:

- cancellation also rounds the time before removing it
- the slot is removed only if the stored reservation ID matches the cancelling reservation ID

That prevents one reservation from deleting another reservation's lock for the same hour.

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
