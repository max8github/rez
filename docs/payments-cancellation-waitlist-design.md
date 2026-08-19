# Payments, Cancellation Refunds, and Waiting Lists — Target Design

This document is a target-state design for adding payments to Rez, plus two features built on top of
payments: a rescue-refund mechanic for late cancellations, and a priority waiting list.

Important:

- this is a design/planning document — none of this is implemented yet
- for exact current implementation status, read [reference/rez-system-overview.md](reference/rez-system-overview.md) and [reference/reservation-locking.md](reference/reservation-locking.md) first
- if this document and the current-state overview conflict, the current-state overview wins
- this document assumes the layering from [conceptual-orchestration-overview.md](conceptual-orchestration-overview.md) (interaction surface → AI intent → booking orchestration → reservation core)

## Purpose

Today, booking through Rez is free: a facility grants courts away at no charge, and Rez has no concept of
money anywhere in the codebase. This document proposes how to introduce real payment (facility gets paid,
Rez takes a percentage) without breaking the reservation core's genericity, and how two derived features —
the late-cancellation rescue refund and the waiting list — build on that payment layer.

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

The existing implicit unit of contention: a specific `(resourceId, dateTime)` pair. Both the rescue-refund
feature and the waiting-list feature are triggered by state changes on a specific slot, independent of which
`ReservationEntity` currently owns it — this requires a query path that doesn't exist today (see
`SlotPaymentView` below).

### Payment

A new economic identity attached to a reservation: price, payer, and payment lifecycle state. Distinct from
`ReservationEntity`'s booking-correctness state, so a new `PaymentEntity` is proposed rather than overloading
`ReservationState`.

### Commitment window and commitment cutoff

- **Commitment window** — the facility-configured span (`PricingPolicy.commitmentWindow`, a duration, e.g. 3
  days) immediately before a slot's start, during which a reservation is no longer freely cancellable.
  *Before* the commitment window, cancelling is free. *Within* the commitment window, cancelling triggers the
  rescue-refund mechanic (§2) instead of a normal cancellation.
- **Commitment cutoff** — the specific instant a given reservation enters its commitment window:
  `max(bookingTime, slotStart − commitmentWindow)`. Before it, no `PaymentEntity` exists yet and Stripe is
  never involved for that reservation. At it (or immediately, if the booking itself happened after this point
  already — e.g. booked the day before the slot), a payment hold is created.
- **Resolution point** — where the commitment window ends: `slotStart` itself — not slot *end*; once a slot
  has actually started there's no realistic scenario left where someone else rescues it — or a
  facility-configurable buffer shortly before `slotStart` if facilities need prep-time certainty (open, see
  below). This is when the hold resolves: captured by default, voided if rescued (§2).

### Payment hold — what it actually means financially

A Stripe `PaymentIntent` created with `capture_method: manual`, once confirmed, is what this document calls a
**hold** — the two terms name the same thing at different levels. `PaymentIntent` is Stripe's API object
tracking the whole lifecycle (`requires_payment_method → requires_confirmation → requires_capture →
succeeded`/`canceled`); "hold" is the everyday name for what that `PaymentIntent` *is*, financially, while
sitting in `requires_capture`.

It's more than a promise: the card network has verified the card and reserved that amount against it — it
shows as a *pending* transaction on the cardholder's statement, reducing what they can spend elsewhere, even
though no money has moved to Rez or the facility yet. Compared to doing nothing until the resolution point:
creating the hold at the commitment cutoff verifies the card actually works with days of runway instead of
failing silently right when the court would otherwise be handed away, and turns **capture** into a
near-guaranteed follow-up call on an already-authorized amount rather than a fresh charge attempt that could
itself fail.

**Captured** means the reserved amount is actually transacted — Stripe pulls the funds for real, settlement
follows, and this is the point money genuinely moves (to Rez as merchant of record, then via
`createTransferFromCharge`-style transfer to the facility's connected account). **Voided** (cancelling an
unconsumed hold) releases the reservation of funds — nothing ever transacts, no fee to anyone. There is
exactly **one** hold per reservation; it's the single mechanism behind both ordinary payment collection and
the rescue refund (§1/§2).

### Card on file

A saved Stripe payment method attached to a `Customer`, collected once at booking time (or reused from an
earlier booking) — separate from, and prior to, the payment hold. Putting a card on file charges or holds
nothing by itself; it's what makes the later, unattended hold-creation at the commitment cutoff possible at
all. See §1.

### Rescue refund, rescue booker, rescued cancellation

The player who books a slot freed up by a late cancellation is the **rescue booker**; a cancellation that
gets bailed out this way is a **rescued cancellation**. See §2 for the mechanics.

### Slot hold

A time-boxed exclusivity claim on a `(resourceId, dateTime)` for one player — an unrelated concept to the
payment hold above, needed for the waiting list's confirmation window ("this court is free, reply within 10
minutes to claim it"). Lives entirely in the orchestration layer via `WaitlistEntity` (§3); the reservation
core (`ResourceEntity`) is never aware of it and needs no changes.

## Proposed Model

### 1. Payment core

New components:

- **`PaymentEntity`** (event-sourced, keyed by a payment id, referenced from `ReservationState` via a new
  `paymentId` field). States: `NONE → AUTHORIZED → CAPTURED / VOIDED / REFUNDED / FAILED`. Wraps a Stripe
  `PaymentIntent` id, amount, currency, the facility's Stripe connected-account id, and Rez's application-fee
  cut.
- **`PricingPolicy`** — price per slot, Rez's commission percentage, and `commitmentWindow`. Lives on
  `FacilityEntity` as a contract-level default (plus the facility's Stripe connected-account id once
  onboarded), with an optional per-`ResourceEntity` override for differently-priced courts.
- **`SlotPaymentView`** — a new Akka View keyed by `resourceId + dateTime`, sourced from `PaymentEntity` /
  `ReservationEntity` events. This is the lookup the rescue-refund behavior (§2) needs: "is there still an
  open hold on exactly this slot?"
- **`PlayerPaymentProfile`** — a new, deliberately minimal component mapping stable player identity (Telegram
  user id today; origin-agnostic key long-term, consistent with `MemberDirectory` in
  `conceptual-orchestration-overview.md`) to a Stripe `customerId` and default `paymentMethodId`. This is the
  one new piece of "who is this player, payment-wise" Rez needs to hold locally — kept as minimal as the rest
  of Rez's member-data philosophy: nothing beyond what's needed to reuse a saved card.
- **`StripeWebhookEndpoint`** — mirrors `hit-backend`'s equivalent. Receives `setup_intent.succeeded` /
  `payment_method.attached` (populates `PlayerPaymentProfile` after card-on-file collection) and the
  `PaymentIntent` lifecycle events needed to reconcile `PaymentEntity`.

**Card on file — collected at booking time, not at the commitment cutoff.** The hold needs a payment method to
authorize against, and asking for it at the commitment cutoff would mean prompting the player out of nowhere,
days or weeks after they last talked to the bot — bad UX, and mechanically impossible besides, since a backend
Timer can't pop a card form onto someone's phone. So card collection and hold creation happen at different
times, for different reasons:

- **At booking time**: `CourtBookingWorkflow` checks `PlayerPaymentProfile` for the requester. A returning
  player with a Stripe customer + saved payment method already on file sees nothing different in the
  conversation at all. A first-time player gets a Stripe-hosted link (Checkout in setup mode, or a Payment
  Link — there's no native card form to embed in a Telegram message) to enter their card once; Rez learns the
  result via `StripeWebhookEndpoint` and populates `PlayerPaymentProfile`. This happens once per player, not
  once per booking — same "ask once" behavior as Hit's own `stripeCustomerId`, set "at registration or first
  booking" (`hit-backend/docs/reference/stripe-connect.md`).
- **At the commitment cutoff**: the hold's `PaymentIntent` is created and confirmed **off-session**, using the
  payment method already in `PlayerPaymentProfile` — no player interaction, no message sent, purely a
  Timer-triggered backend step.

**Known gap, not solved here**: off-session confirmation can occasionally fail with `authentication_required`
even against a saved card — a bank-side 3D-Secure/SCA re-check, rare but real. What happens then isn't
designed yet — see Open Questions.

**Timing — anchored to the commitment cutoff, not to booking time.** Rather than charging at booking time, or
holding uncaptured all the way from booking to slot time, the hold is created at the reservation's
**commitment cutoff** (see Terminology above): `max(bookingTime, slotStart − commitmentWindow)`. A court
booked two weeks out with a 3-day commitment window has nothing to do with Stripe for the first eleven
days — cancelling in that period is genuinely free, no `PaymentEntity` exists yet, nothing to void, nothing to
refund. An Akka Timer for the commitment cutoff is set at booking time; if the booking itself happens after
that point already (e.g. booked the day before the slot), the commitment cutoff is now, and the hold is
created immediately with no waiting.

When the commitment-cutoff Timer fires: create `PaymentEntity` with a `PaymentIntent` on **manual capture** —
a hold, not a charge. A second Akka Timer is set for the **resolution point** (`slotStart`, or a
facility-configurable cutoff before it).

When the resolution-point Timer fires: **capture** the hold by default. For an ordinary, never-cancelled
booking, this is how it actually gets paid — landing at (or just before) slot time, which happens to be close
to how Hit's own session-completion capture already works
(`hit-backend/docs/reference/stripe-connect.md`) — convenient, not load-bearing; neither system was built to
depend on the other's timing. If the reservation was cancelled within its commitment window and the slot got
resold before resolution, the hold is **voided** instead of captured — see §2.

This is one mechanism serving both purposes: ordinary payment collection, and — via what the same hold does
between the commitment cutoff and the resolution point — the rescue-refund logic. There's no second hold
type, no separate penalty-specific Timer.

**Why this is Stripe-safe regardless of how far ahead a court is booked.** A reservation's hold is open for at
most `commitmentWindow` (commitment cutoff to resolution point) — never for the full booking-to-slot-time
span. Stripe guarantees authorizations are capturable for up to ~7 days (shorter on some card networks), so
`commitmentWindow` needs to stay safely under that — worth an explicit validation cap on `PricingPolicy`
(e.g. reject anything over a few days) rather than an implicit assumption. Realistic for any court
cancellation policy (hours to a couple of days), so this shouldn't bind in practice.

Worth checking separately: Hit already does "hold at booking, capture at completion" for session bookings
(`hit-backend/docs/reference/stripe-connect.md`). If Hit's hold spans booking-to-completion unconditionally
(rather than being anchored to something like a commitment point), and sessions can be booked more than ~7
days out, Hit likely has this same latent issue today, independent of anything in Rez. Not this document's
scope to fix, but worth flagging to whoever owns that code.

**Stripe routing — decided.** Rez is merchant of record: **destination charges**, not direct charges on the
facility's connected account. Rez owns refund/dispute handling directly against `PaymentEntity`'s state
machine, and automatically transfers the facility's share to their connected account per the existing
`createTransferFromCharge`-style pattern (see hit-backend's `StripeService` for the equivalent teacher-payout
mechanics — same shape, different payee). The facility's Stripe dashboard shows a transfer received, not a
charge made; disputes go to Rez, not the facility.

Manual invoicing (no Stripe at all, for a first facility partner) is a reasonable bridge but isn't designed
further here.

### 2. Rescue refund

A later booker — the **rescue booker** — "rescues" the cancelling player from the penalty by booking the slot
themselves before it resolves. A cancellation that gets bailed out this way is a **rescued cancellation**.

Reframed simply: *the facility is guaranteed payment for the slot exactly once, from whoever ends up holding
it last* — not a fine that stacks on top of a resale. This reframing costs nothing extra to build: it isn't a
second mechanism, it's just what already happens to §1's hold when a reservation is cancelled within its
commitment window.

1. PlayerA cancels within their commitment window (i.e. after the commitment cutoff has passed). A
   `PaymentEntity` hold already exists for this reservation (§1) — created back when the commitment-cutoff
   Timer fired, well before the cancellation. `ReservationEntity.cancelRequest()` (existing path) does **not**
   touch the hold at all; the reservation just cancels normally, and the resolution-point Timer from §1 keeps
   running, untouched.
2. If a rescue booker (PlayerB) books and pays for the same `(resourceId, dateTime)` before that timer fires:
   PlayerB is necessarily booking after PlayerA's commitment cutoff already passed, so PlayerB's *own*
   commitment cutoff (`max(playerBBookingTime, slotStart − commitmentWindow)`) is simply *now* — PlayerB's
   hold is created and captured immediately. That capture's completion handler queries `SlotPaymentView`,
   finds PlayerA's still-`AUTHORIZED` hold on the same slot, and **voids** it — PlayerA is never charged. This
   is the rescue. PlayerB's payment proceeds through the normal facility/Rez split.
3. If the resolution-point Timer fires first with no rescue booker: the hold **captures**, exactly as it would
   have if PlayerA had never cancelled — the facility is paid by PlayerA, as if the slot had gone unsold. From
   the payment side this is indistinguishable from an ordinary uncancelled booking resolving; the "penalty" is
   just what capturing an unrescued hold looks like.

No second `PaymentEntity`, no cancellation-triggered hold creation, no double-authorization risk — there is
only ever one hold per reservation, anchored at the commitment cutoff regardless of whether that reservation
later becomes a rescued cancellation. Cancellation just changes which of the two resolution outcomes (capture
vs. void) ends up happening.

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
- `PlayerPaymentProfile` — new, minimal component mapping player identity to Stripe `customerId` +
  `paymentMethodId` (§1)
- `StripeWebhookEndpoint` — receives card-on-file and `PaymentIntent` lifecycle events (§1)
- `WaitlistEntity`, `WaitlistState`, `WaitlistEvent` — new package, e.g. `com.rezhub.reservation.waitlist`
- Stripe client wrapper (module boundary TBD — likely a sibling module to `telegramnotifier` /
  `notifierstub`, following the existing `spi` + swappable-implementation pattern)

### Touched components

- `ReservationEntity` / `ReservationState` — gains `paymentId`; `FULFILLED` triggers scheduling of the
  commitment-cutoff Timer (exact owning component TBD — likely a `CourtBookingWorkflow` step rather than the
  entity itself, to keep `ReservationEntity` from taking on payment-timing responsibility)
- `CourtBookingWorkflow` — gains a card-on-file check before submitting a booking, and the commitment/
  resolution payment flow after fulfillment (§1); gains a waitlist-offer step on rejection; gains an
  active-offer exclusivity check before submitting a booking (§3)
- `FacilityEntity` — gains `PricingPolicy` (price, commission %, commitment window) and Stripe
  connected-account id
- `BookingTools` — new `@FunctionTool` methods: join waitlist, confirm waitlist offer; `BookingAgent`'s system
  prompt loses its "payments out of scope" line
- `ResourceAction` — gains a step to consult `WaitlistEntity` on `ReservationCanceled`

### Left alone

- `ResourceEntity` / `ResourceState` / `Booking` — genuinely untouched, not just unchanged in shape. The
  reservation core still only ever knows free-or-booked; both the rescue refund (§2) and waitlist priority
  (§3) are enforced entirely outside it, in the orchestration layer.
- The reservation-core locking correctness itself (`ReservationEntity`/`ResourceEntity`'s core competition and
  event handshake) is unchanged — payments and the waitlist extend the system around it, not the core.

## Migration Sequence

### Phase 1: Payment core

- `PaymentEntity`, `PricingPolicy` (price, commission %, commitment window) on `FacilityEntity`,
  Stripe connected-account onboarding
- Destination charges (Rez as merchant of record) — decided, see §1 above
- Commitment-cutoff Timer (scheduled at booking) → payment hold created → resolution-point Timer →
  capture-by-default — decided, see §1 above (supersedes two earlier revisions of this section: "hold from
  booking to slot time" and, before that, "charge immediately at booking" — both had real problems, see git
  history if curious)
- Compensation (cancel reservation) on payment-authorization failure
- Independently shippable — this alone makes Rez charge for bookings

### Phase 2: Rescue refund

- Depends entirely on Phase 1's hold/timer mechanism plus `SlotPaymentView` — no new payment primitive, see
  §2 above
- The only genuinely new piece is the rescue-detection lookup in the payment-completion handler; the
  commitment-window policy itself is already part of Phase 1's `PricingPolicy`

### Phase 3: Waiting list

- Depends on `WaitlistEntity` (queue + active offer) and Timers, plus the exclusivity check in
  `CourtBookingWorkflow.book()`; no dependency on `ResourceEntity` at all (§3). Loosely depends on Phase 1
  (final confirm reuses the normal paid-booking path)
- Can be built in parallel with Phase 2 — they only share the `resourceId + dateTime` lookup convention, not
  code

## Open Questions

1. ~~Stripe Connect: destination charges vs. direct charges on the facility's account.~~ **Resolved** —
   destination charges, Rez as merchant of record. See §1 above.
2. Resolution-point cutoff: capture exactly at slot start, or some facility-configurable buffer before it
   (facilities may need prep-time certainty rather than a last-second cancellation-to-capture)? Applies
   uniformly to every booking now (§1), not just late cancellations.
3. Waitlist confirmation window length, whether a player can queue for multiple slots or multiple overlapping
   waitlists at once, and whether an active offer should ever go to more than one person simultaneously (this
   design assumes strictly one-at-a-time, front-of-queue-only — see §3).
4. Does `BookingEndpoint`'s direct-HTTP cancel path (bypassing `BookingApplicationService` today) need any
   special handling, or does it just work unchanged now that cancellation doesn't touch the payment hold at
   all (§2)?
5. Who configures `PricingPolicy` per facility, and through what interface — is this an admin-only Telegram
   command, or does it require a non-conversational admin surface?
6. ~~Could Phase 2 reuse Phase 1's hold instead of creating a second one?~~ **Resolved** — yes, by
   construction: anchoring the hold to the commitment cutoff (§1) rather than to booking time makes it the
   same hold Phase 2 needs, with no double-authorization risk. See §1/§2.
7. Should `commitmentWindow` be hard-capped in `PricingPolicy` validation (e.g. reject anything over a
   few days) to stay safely under Stripe's ~7-day authorization limit, or just documented as a guideline for
   facility onboarding?
8. Off-session hold-creation failure at the commitment cutoff (`authentication_required`, or the saved card
   simply got declined/expired since it was put on file) — what's the fallback? Notify the player with a
   re-authentication/new-card link and a grace window before the resolution point, most likely — but what
   happens if that grace window also lapses? Force-cancel the reservation for free (not really the player's
   fault their card needs rechecking), or let it fall through to the same unrescued-penalty outcome as an
   ordinary late cancellation? Not designed yet.

## Recommendation Summary

- Introduce payment as a first-class new entity (`PaymentEntity`) joined to reservations by id, not folded
  into `ReservationState` — keeps the reservation core's booking-correctness concern separate from money.
- Anchor the payment hold to the reservation's **commitment cutoff** (`slotStart − commitmentWindow`, or
  booking time if later) rather than to booking time or slot time alone. This is what lets one hold safely
  serve both ordinary payment collection and the rescue refund (§1/§2) — its lifetime is always bounded by
  the facility's own commitment-window policy, staying well under Stripe's 7-day authorization limit by
  construction, not coincidence.
- Treat waitlist "priority" as an orchestration-layer policy (`WaitlistEntity`'s active-offer field, checked
  in `CourtBookingWorkflow.book()`), not a new primitive on the reservation core. `ResourceEntity` stays
  exactly as generic as it is today.
- Separate card collection from hold creation. A player provides card details once, at booking time (skipped
  entirely for returning players via `PlayerPaymentProfile`); the commitment-cutoff hold is created
  off-session against that saved card, with no prompt or message sent at that moment. Conflating the two would
  mean asking someone for a credit card out of nowhere, days or weeks after they last opened the chat.
- Ship payments (Phase 1) first — it's independently valuable and both other features build on it or its
  supporting `SlotPaymentView`.
