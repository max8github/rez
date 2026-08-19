# Payments, Cancellation Refunds, and Waiting Lists — Target Design

This document is a target-state design for adding payments to Rez, plus two features built on top of
payments: a resale-refund mechanic for late cancellations, and a priority waiting list.

Important:

- this is a design/planning document — none of this is implemented yet
- for exact current implementation status, read [reference/rez-system-overview.md](reference/rez-system-overview.md) and [reference/reservation-locking.md](reference/reservation-locking.md) first
- if this document and the current-state overview conflict, the current-state overview wins
- this document assumes the layering from [conceptual-orchestration-overview.md](conceptual-orchestration-overview.md) (interaction surface → AI intent → booking orchestration → reservation core)

## Purpose

Today, booking through Rez is free: a facility grants courts away at no charge, and Rez has no concept of
money anywhere in the codebase. This document proposes how to introduce real payment (facility gets paid,
Rez takes a percentage) without breaking the reservation core's genericity, and how two derived features —
late-cancellation-with-resale-refund and waiting lists — build on that payment layer.

## Motivation

- Facilities need to get paid for courts when the facility isn't offering them for free.
- Rez, as the platform, needs a commission mechanism.
- A cancellation penalty that simply enriches the facility whenever a slot gets resold anyway is unfair to
  the cancelling player — the facility should only be paid once per slot.
- A player who wants a full slot but finds it already booked should be able to register interest and get
  first refusal if it frees up, instead of having to poll Telegram.
- Rez has no mobile app: all of this must work as Telegram (and Matrix) messages interpreted by an LLM
  calling tools — no UI affordances like "tap to confirm" exist, only conversational replies within a time
  window.

## Current Situation (verified against code)

- `ResourceEntity`/`ResourceState`
  (`reservation/reservation/src/main/java/com/rezhub/reservation/resource/`) track bookings in a
  `NavigableMap<LocalDateTime, Booking>` keyed by real (unrounded) start time. `Booking` is
  `record Booking(LocalDateTime endTime, String reservationId)` — no owner identity beyond the
  `reservationId`, no price, no hold/soft-lock concept. A slot is either free or fully booked.
- `ReservationEntity`/`ReservationState`
  (`reservation/reservation/src/main/java/com/rezhub/reservation/reservation/`) is event-sourced with states
  `INIT → COLLECTING/SELECTING → FULFILLED → CANCELLED` (or `UNAVAILABLE`). Cancellation is already a
  two-step handshake: `cancelRequest()` persists `CancelRequested`, `ReservationAction` unlocks the resource,
  `ResourceEntity` emits `ReservationCanceled`, which `ResourceAction` uses to call `ReservationEntity.cancel()`,
  finally persisting `ReservationCancelled`. No price, payment, or penalty field exists on the reservation DTO
  (`emails`, `dateTime`, `durationMinutes`, `resourceId(s)`, `recipientId`, `originSystem` only).
- Orchestration (`orchestration/*`): `BookingApplicationService` → `BookingContextResolverAkka` →
  `BookingWorkflow` (`CourtBookingWorkflow`) → `ReservationGatewayAkka` → `ReservationEntity`. Cancellation is
  routed through this chain from `BookingTools.cancelReservation` (agent path); `BookingEndpoint.cancelBooking`
  (`DELETE /bookings/{id}`, direct HTTP) currently calls `ReservationEntity::cancelRequest` directly, bypassing
  the application service.
- `agent/BookingTools.java` exposes `@FunctionTool` methods: `checkAvailability`, `bookCourt`,
  `cancelReservation`, `getReservationDetails`, `resolveDateTime`. `BookingAgent`'s system prompt explicitly
  states handling payments/subscriptions is out of scope.
- Grepping the codebase for `price`, `payment`, `stripe`, `penalty`, `waitlist`, `refund` returns nothing
  except that one disclaiming line in the agent prompt. None of this exists today.

## Terminology / New Concepts

### Slot

The existing implicit unit of contention: a specific `(resourceId, dateTime)` pair. Both the resale-refund
feature and the waiting-list feature are triggered by state changes on a specific slot, independent of which
`ReservationEntity` currently owns it — this requires a query path that doesn't exist today (see
`SlotPaymentView` below).

### Payment

A new economic identity attached to a reservation: price, payer, and payment lifecycle state. Distinct from
`ReservationEntity`'s booking-correctness state, so a new `PaymentEntity` is proposed rather than overloading
`ReservationState`.

### Hold (soft lock)

A time-boxed reservation of a slot that isn't a full booking yet — needed for the waiting-list confirmation
window ("this court is free, reply within 10 minutes to claim it"). Nothing like this exists on `ResourceEntity`
today; it currently only knows CONFIRMED-or-free.

## Proposed Model

### 1. Payment core

New components:

- **`PaymentEntity`** (event-sourced, keyed by a payment id, referenced from `ReservationState` via a new
  `paymentId` field). States: `NONE → AUTHORIZED → CAPTURED / VOIDED / REFUNDED / FAILED`. Wraps a Stripe
  `PaymentIntent` id, amount, currency, the facility's Stripe connected-account id, and Rez's application-fee
  cut.
- **`PricingPolicy`** — price per slot and Rez's commission percentage. Lives on `FacilityEntity` as a
  contract-level default (plus the facility's Stripe connected-account id once onboarded), with an optional
  per-`ResourceEntity` override for differently-priced courts.
- **`SlotPaymentView`** — a new Akka View keyed by `resourceId + dateTime`, sourced from `PaymentEntity` /
  `ReservationEntity` events. This is the lookup both the resale-refund feature and (indirectly) the waiting
  list need: "what's the current payment state of exactly this slot?"

Flow: `CourtBookingWorkflow` gains a payment step after `ReservationEntity` reaches `FULFILLED` — create a
`PaymentEntity` with a Stripe `PaymentIntent` on **manual capture** (a hold, not an immediate charge), and only
then consider the booking complete from the player's perspective. Failure to pay should compensate the
reservation (cancel it), the same way a composite-workflow failure would compensate an earlier step per
`conceptual-orchestration-overview.md`'s saga pattern.

**Capture timing — decided.** The hold is captured by an Akka Timer set for the slot's start/end (facility-
configurable), not immediately at booking. This reuses the exact same hold-then-capture-via-Timer primitive
Phase 2 already needs for the late-cancellation penalty (§2 below), so Phase 1 and Phase 2 share one mechanism
instead of introducing two. It also keeps Rez's payment timing origin-agnostic: a direct Telegram booking has
no external "session" to key off, so capture-at-slot-time is the only trigger that makes sense for every
caller uniformly. For Hit-originated bookings specifically, this happens to land at roughly the same real-world
moment as Hit's own session-completion capture (see `hit-backend/docs/reference/stripe-connect.md`) — the two
systems weren't made to depend on each other's timing, they just converge because "slot end" and "session
completion" are approximately the same instant.

**Stripe routing — decided.** Rez is merchant of record: **destination charges**, not direct charges on the
facility's connected account. Rez owns refund/dispute handling directly against `PaymentEntity`'s state
machine, and automatically transfers the facility's share to their connected account per the existing
`createTransferFromCharge`-style pattern (see hit-backend's `StripeService` for the equivalent teacher-payout
mechanics — same shape, different payee). The facility's Stripe dashboard shows a transfer received, not a
charge made; disputes go to Rez, not the facility.

Manual invoicing (no Stripe at all, for a first facility partner) is a reasonable bridge but isn't designed
further here.

### 2. Late cancellation with resale refund

The penalty is reframed as: *the facility is guaranteed payment for the slot exactly once, from whoever ends
up holding it last* — not a fine that stacks on top of a resale.

This maps onto Stripe's authorize/capture split instead of charge-then-refund:

1. PlayerA cancels past the facility's free-cancellation window. `ReservationEntity.cancelRequest()` (existing
   path) additionally creates a `PaymentIntent` with `capture_method: manual` for the penalty amount — a
   **hold**, no money moves. `PaymentEntity` → `AUTHORIZED`.
2. An Akka Timer is set for the slot's original start time (or a facility-configurable cutoff before it).
3. If PlayerB books and pays for the same `(resourceId, dateTime)` before the timer fires, the new payment's
   completion handler queries `SlotPaymentView`, finds PlayerA's `AUTHORIZED` hold, and **voids** it —
   PlayerA is never charged. PlayerB's payment proceeds through the normal facility/Rez split.
4. If the timer fires first with no rebooking, the hold is **captured** — the facility is paid by PlayerA,
   exactly as if the slot had gone unsold.

Card authorization holds are valid for up to ~7 days on Stripe. Since this can only trigger inside a
late-cancellation window (by definition close to the slot start), the auth-to-resolution gap stays well
inside that limit without needing the charge-then-refund fallback.

### 3. Waiting list with priority notify

The novel primitive here is the **hold**, since `ResourceEntity` currently only distinguishes free vs. booked.

- **`WaitlistEntity`**, keyed by `resourceId + dateTime` (same granularity as a `Booking`), holds an ordered
  (FIFO) list of `{playerId, joinedAt, originContext}`. Populated when `ResourceEntity.reserve` rejects a
  request — `BookingWorkflow` / `BookingTools` catch the rejection and the agent offers to register interest.
- `Booking` gains an optional `heldFor` + `holdExpiresAt`. `isReservableAt` treats a HELD (not CONFIRMED)
  entry as blocking everyone except the held-for player, and as non-blocking once `holdExpiresAt` passes.
- When `ResourceEntity` emits `ReservationCanceled` for a slot, a new consumer looks up
  `WaitlistEntity(resourceId#dateTime)`: if non-empty, pop the first entrant, place a HELD booking with a
  short expiry (e.g. 10 minutes), start a Timer, and notify via the existing `telegramnotifier` module
  ("Court 1 at 6pm just opened up — reply to confirm within 10 minutes").
- Confirming routes through the normal `bookCourt` tool call, upgrading HELD → CONFIRMED (and into the
  payment flow from part 1). Timer expiry with no confirmation pops the next entrant and repeats. An empty
  queue leaves the slot open to anyone, as today.
- Multiple players can queue for the same slot; a player already in a queue could in principle join others
  too (see open questions).

## Code Mapping

### New components

- `PaymentEntity`, `PaymentState`, `PaymentEvent` — new package, e.g. `com.rezhub.reservation.payment`
- `SlotPaymentView` — Akka View, keyed by `resourceId + dateTime`
- `WaitlistEntity`, `WaitlistState`, `WaitlistEvent` — new package, e.g. `com.rezhub.reservation.waitlist`
- Stripe client wrapper (module boundary TBD — likely a sibling module to `telegramnotifier` /
  `notifierstub`, following the existing `spi` + swappable-implementation pattern)

### Touched components

- `ResourceEntity` / `ResourceState` — `Booking` record gains `heldFor` + `holdExpiresAt`;
  `isReservableAt` gains hold-awareness
- `ReservationEntity` / `ReservationState` — gains `paymentId`
- `CourtBookingWorkflow` — gains a payment step after fulfillment; gains a waitlist-offer step on rejection
- `FacilityEntity` — gains `PricingPolicy` (price, commission %) and Stripe connected-account id
- `BookingTools` — new `@FunctionTool` methods: join waitlist, confirm waitlist offer; `BookingAgent`'s system
  prompt loses its "payments out of scope" line
- `ResourceAction` — gains a step to consult `WaitlistEntity` on `ReservationCanceled`

### Left alone

- The reservation-core locking correctness itself (`ReservationEntity`/`ResourceEntity`'s core competition and
  event handshake) is unchanged in shape — payments and holds extend it, they don't replace it.

## Migration Sequence

### Phase 1: Payment core

- `PaymentEntity`, `PricingPolicy` on `FacilityEntity`, Stripe connected-account onboarding
- Destination charges (Rez as merchant of record) — decided, see §1 above
- `CourtBookingWorkflow` payment step after `FULFILLED`: manual-capture hold, captured by a Timer at slot
  start/end rather than immediately — decided, see §1 above
- Compensation (cancel reservation) on payment-authorization failure
- Independently shippable — this alone makes Rez charge for bookings

### Phase 2: Late cancellation + resale refund

- Depends on Phase 1 plus `SlotPaymentView`
- Facility-level free-cancellation-window config
- Manual-capture hold on late cancel, Timer at cutoff, void-on-resale / capture-on-timeout

### Phase 3: Waiting list

- Depends on the hold primitive on `ResourceEntity` + Timers; loosely depends on Phase 1 (final confirm
  reuses the normal paid-booking path)
- Can be built in parallel with Phase 2 — they only share the `resourceId + dateTime` lookup convention, not
  code

## Open Questions

1. ~~Stripe Connect: destination charges vs. direct charges on the facility's account.~~ **Resolved** —
   destination charges, Rez as merchant of record. See §1 above.
2. Penalty cutoff: capture exactly at slot start, or some buffer before it (facilities may need prep-time
   certainty rather than a last-second cancellation-to-capture)? Now doubles as the same Timer Phase 1's
   normal-booking capture uses (see §1) — one cutoff decision governs both, not two separate ones.
3. Waitlist confirmation window length, and whether a player can queue for multiple slots or multiple
   overlapping waitlists at once.
4. Does `BookingEndpoint`'s direct-HTTP cancel path (bypassing `BookingApplicationService` today) also need
   to trigger the penalty-hold logic, or is penalty logic scoped to the agent/Telegram path only?
5. Who configures `PricingPolicy` per facility, and through what interface — is this an admin-only Telegram
   command, or does it require a non-conversational admin surface?
6. **New, raised by the Phase 1 capture-timing decision above:** since a normal booking (Phase 1) now already
   holds an uncaptured `PaymentEntity` for the full slot price until slot time, does Phase 2's late-cancellation
   penalty need its *own* second `PaymentEntity`/hold at all — or can it just reuse the original booker's
   already-open Phase 1 hold (void it if the slot resells before the cutoff Timer, otherwise let it capture
   exactly as it would have if never cancelled)? Creating a second, separate hold on top of an already-open one
   for the same slot would double-authorize the same card. Worth resolving before implementing §2 — it may
   simplify Phase 2 down to "conditionally void or don't void the Phase 1 hold," with no new payment primitive.

## Recommendation Summary

- Introduce payment as a first-class new entity (`PaymentEntity`) joined to reservations by id, not folded
  into `ReservationState` — keeps the reservation core's booking-correctness concern separate from money.
- Model the late-cancellation penalty as an authorize-then-capture hold, not an immediate charge-then-refund
  — it means PlayerA is never actually charged in the common case where the slot resells, and it reuses the
  Timer primitive the waiting list also needs.
- Treat "hold with expiry" as a genuinely new primitive on the reservation core (`ResourceEntity`), since both
  the waiting list and (indirectly) the penalty-vs-resale race depend on a slot being neither fully free nor
  fully booked for a bounded window.
- Ship payments (Phase 1) first — it's independently valuable and both other features build on it or its
  supporting `SlotPaymentView`.
