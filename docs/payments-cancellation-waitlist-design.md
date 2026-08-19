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

### Two different meanings of "hold"

This document uses "hold" for two unrelated things — worth disambiguating up front:

- **Payment hold**: a Stripe `PaymentIntent` on manual capture — an authorization with no money moved yet.
  Used only for the late-cancellation penalty (§2). Normal bookings (§1) capture immediately; see the Stripe
  7-day authorization limit discussion in §1 for why.
- **Slot hold**: a time-boxed exclusivity claim on a `(resourceId, dateTime)` for one player — needed for the
  waiting list's confirmation window ("this court is free, reply within 10 minutes to claim it"). This lives
  entirely in the orchestration layer via `WaitlistEntity` (§3); the reservation core (`ResourceEntity`) is
  never aware of it and needs no changes — from the core's point of view the slot is simply free the moment
  it's cancelled.

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
`PaymentEntity`, **charge immediately** (`PaymentIntent` with automatic capture), and only then consider the
booking complete from the player's perspective. Failure to pay should compensate the reservation (cancel it),
the same way a composite-workflow failure would compensate an earlier step per
`conceptual-orchestration-overview.md`'s saga pattern.

**Capture timing — corrected.** An earlier revision of this doc proposed holding every booking's payment
uncaptured until slot start/end, reusing one Timer mechanism for both normal bookings and the late-cancellation
penalty. That doesn't work: Stripe card authorizations are only guaranteed capturable for **up to 7 days**
(shorter on some card networks), and a court can legitimately be booked weeks in advance — a manual-capture
hold sitting open that long risks silently expiring before slot time, with no notification and no automatic
retry. Reusing a hold across an unbounded advance-booking window isn't safe; reusing it across the
late-cancellation window (§2) is, because "late" cancellation is by definition close to the slot's start.

So Phase 1 reverts to the standard pattern: **charge in full at booking time**, same as most reservation
systems. Phase 2's manual-capture hold (§2) is a separate, narrower mechanism used only for the cancellation
penalty, where the authorize-to-resolve gap is short by construction. The two no longer share a Timer.

Worth checking separately: Hit already does "hold at booking, capture at completion" for session bookings
(`hit-backend/docs/reference/stripe-connect.md`) with no visible handling of the same 7-day limit — if Hit
allows sessions to be booked more than ~7 days out, it likely has this exact latent issue today, independent
of anything in Rez. Not this document's scope to fix, but worth flagging to whoever owns that code.

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

At its core, this is exactly the consumer-on-cancellation-events design it sounds like it should be: a new
consumer subscribes to `ResourceEntity`'s `ReservationCanceled` events, filters by `(resourceId, dateTime)`,
and sends a Telegram notification when a slot someone's interested in frees up. No change to the reservation
core is needed for that part.

The one thing plain "notify, then let them call the normal book tool" doesn't deliver is *priority*. The
instant a cancellation event fires, `ResourceEntity` sees the slot as simply free again — it has no notion of
who was waiting. If the notified player and everyone else who happens to check that slot are all racing to
call `bookCourt` through the normal path, whoever completes the call first wins, waitlisted or not. That's
"you get told first," not "you get the chance, for a limited time, to reserve it" — which is what was asked
for, and the only thing that makes an ordered queue of multiple waiters meaningful rather than a free-for-all
every time a slot opens up.

Delivering real priority needs *some* mechanism that makes the slot briefly unavailable to everyone except the
front-of-queue player — but that mechanism doesn't have to live in `ResourceEntity`. It can live entirely in
the orchestration layer instead:

- **`WaitlistEntity`**, keyed by `resourceId + dateTime`, holds an ordered (FIFO) queue of
  `{playerId, joinedAt, originContext}`, plus a single **active offer** field:
  `{playerId, offeredAt, expiresAt}` or empty. Populated when `ResourceEntity.reserve` rejects a request —
  `BookingWorkflow` / `BookingTools` catch the rejection and the agent offers to register interest.
- On `ReservationCanceled`, a new consumer looks up `WaitlistEntity(resourceId#dateTime)`. If the queue is
  non-empty, it pops the first entrant into the active-offer field with a short expiry (e.g. 10 minutes),
  starts a Timer, and notifies via the existing `telegramnotifier` module ("Court 1 at 6pm just opened up —
  reply to confirm within 10 minutes").
- **`CourtBookingWorkflow.book()`** gains one check before forwarding to `ReservationGateway.submit()`: if
  `WaitlistEntity` for that slot has an active, unexpired offer whose `playerId` doesn't match the requester,
  decline with "this slot's currently offered to someone ahead of you — want to join the queue?" instead of
  submitting. Everyone else — including the front-of-queue player themselves, and anyone at all once the
  offer is empty or expired — books through the completely ordinary path.
- Confirming is just the front-of-queue player calling `bookCourt` normally within the window; it succeeds
  because the check above passes for them specifically (and proceeds into the payment flow from §1). Timer
  expiry with no confirmation clears the active offer and pops the next entrant, repeating the notify step.
  An empty queue, or an expired and un-refilled offer, leaves the slot open to anyone, exactly as today.

This keeps `ResourceEntity` / `Booking` completely untouched — the reservation core still only ever sees
"free" or "booked," and the priority guarantee is enforced one layer up, as a policy check rather than new
locking state. It also matches this repo's existing architectural goal
(`conceptual-orchestration-overview.md`) of keeping the reservation core generic and pushing business-specific
rules into orchestration.

## Code Mapping

### New components

- `PaymentEntity`, `PaymentState`, `PaymentEvent` — new package, e.g. `com.rezhub.reservation.payment`
- `SlotPaymentView` — Akka View, keyed by `resourceId + dateTime`
- `WaitlistEntity`, `WaitlistState`, `WaitlistEvent` — new package, e.g. `com.rezhub.reservation.waitlist`
- Stripe client wrapper (module boundary TBD — likely a sibling module to `telegramnotifier` /
  `notifierstub`, following the existing `spi` + swappable-implementation pattern)

### Touched components

- `ReservationEntity` / `ReservationState` — gains `paymentId`
- `CourtBookingWorkflow` — gains a payment step after fulfillment; gains a waitlist-offer step on rejection;
  gains an active-offer exclusivity check before submitting a booking (§3)
- `FacilityEntity` — gains `PricingPolicy` (price, commission %) and Stripe connected-account id
- `BookingTools` — new `@FunctionTool` methods: join waitlist, confirm waitlist offer; `BookingAgent`'s system
  prompt loses its "payments out of scope" line
- `ResourceAction` — gains a step to consult `WaitlistEntity` on `ReservationCanceled`

### Left alone

- `ResourceEntity` / `ResourceState` / `Booking` — genuinely untouched, not just unchanged in shape. The
  reservation core still only ever knows free-or-booked; both the payment penalty (§2) and waitlist priority
  (§3) are enforced entirely outside it, in the orchestration layer.
- The reservation-core locking correctness itself (`ReservationEntity`/`ResourceEntity`'s core competition and
  event handshake) is unchanged — payments and the waitlist extend the system around it, not the core.

## Migration Sequence

### Phase 1: Payment core

- `PaymentEntity`, `PricingPolicy` on `FacilityEntity`, Stripe connected-account onboarding
- Destination charges (Rez as merchant of record) — decided, see §1 above
- `CourtBookingWorkflow` payment step after `FULFILLED`: charge immediately (automatic capture) — corrected,
  see §1 above (an earlier revision proposed a hold captured at slot time; reverted due to Stripe's 7-day
  authorization limit)
- Compensation (cancel reservation) on payment failure
- Independently shippable — this alone makes Rez charge for bookings

### Phase 2: Late cancellation + resale refund

- Depends on Phase 1 plus `SlotPaymentView`
- Facility-level free-cancellation-window config
- Manual-capture hold on late cancel, Timer at cutoff, void-on-resale / capture-on-timeout

### Phase 3: Waiting list

- Depends on `WaitlistEntity` (queue + active offer) and Timers, plus the exclusivity check in
  `CourtBookingWorkflow.book()`; no dependency on `ResourceEntity` at all (§3). Loosely depends on Phase 1
  (final confirm reuses the normal paid-booking path)
- Can be built in parallel with Phase 2 — they only share the `resourceId + dateTime` lookup convention, not
  code

## Open Questions

1. ~~Stripe Connect: destination charges vs. direct charges on the facility's account.~~ **Resolved** —
   destination charges, Rez as merchant of record. See §1 above.
2. Penalty cutoff: capture exactly at slot start, or some buffer before it (facilities may need prep-time
   certainty rather than a last-second cancellation-to-capture)?
3. Waitlist confirmation window length, whether a player can queue for multiple slots or multiple overlapping
   waitlists at once, and whether an active offer should ever go to more than one person simultaneously (this
   design assumes strictly one-at-a-time, front-of-queue-only — see §3).
4. Does `BookingEndpoint`'s direct-HTTP cancel path (bypassing `BookingApplicationService` today) also need
   to trigger the penalty-hold logic, or is penalty logic scoped to the agent/Telegram path only?
5. Who configures `PricingPolicy` per facility, and through what interface — is this an admin-only Telegram
   command, or does it require a non-conversational admin surface?
6. ~~Since Phase 1 held payment uncaptured until slot time, could Phase 2 reuse that same hold instead of a
   second one?~~ **Moot** — Phase 1 reverted to immediate capture at booking (see §1's correction), so there's
   no open Phase 1 hold to reuse. Phase 2's penalty hold is its own, separate authorization.

## Recommendation Summary

- Introduce payment as a first-class new entity (`PaymentEntity`) joined to reservations by id, not folded
  into `ReservationState` — keeps the reservation core's booking-correctness concern separate from money.
- Model the late-cancellation penalty as an authorize-then-capture hold, not an immediate charge-then-refund
  — it means PlayerA is never actually charged in the common case where the slot resells. This hold is
  necessarily separate from Phase 1's payment, and only safe because the late-cancellation window is short by
  construction — an open-ended hold from booking time to slot time is not, per Stripe's 7-day authorization
  limit (§1).
- Treat waitlist "priority" as an orchestration-layer policy (`WaitlistEntity`'s active-offer field, checked
  in `CourtBookingWorkflow.book()`), not a new primitive on the reservation core. `ResourceEntity` stays
  exactly as generic as it is today.
- Ship payments (Phase 1) first — it's independently valuable and both other features build on it or its
  supporting `SlotPaymentView`.
