# Feature Specification: Payment Core

- **Feature Branch**: `002-payment-core`
- **Created**: 2026-09-05
- **Status**: Draft
- **Input**: User description: "Start Rez's Payment core — and only Payment core (Phase 1 of rez/docs/payments-cancellation-waitlist-design.md): the Stripe hold anchored to commitment cutoff, PlayerPaymentProfile. Do not scope in Rescue refund or Waiting list — they're Phases 2 and 3 of the same doc, each explicitly sequenced after this one and dependent on it; don't spec or plan them yet. This is now unblocked: Stage 3 of cross-product identity is done — PlayerPaymentProfile can key off the real identity userId Rez's TelegramEndpoint already resolves on every message, no separate Rez sign-in needed."

This spec covers **Phase 1 only** of `docs/payments-cancellation-waitlist-design.md` (§1 "Proposed Model" and "Migration Sequence → Phase 1"). Rescue refund (§2 / Phase 2) and the waiting list (§3 / Phase 3) are explicitly out of scope — both are sequenced after this feature and depend on it; this spec does not design them, beyond noting where Phase 1 must leave room for them (e.g. `SlotPaymentView` existing but having no consumer yet).

The mechanism described in the design doc — hold anchored to the reservation's commitment cutoff, card-on-file collected separately from hold creation, destination charges with Rez as merchant of record — is treated as converged (it went through several rounds of correction; see the doc's Migration Sequence note). This spec does not re-derive that mechanism; it fills in what the doc leaves genuinely open for Phase 1.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A returning player is charged for a court with no extra steps (Priority: P1)

A player who has already put a card on file with Rez (in an earlier booking, or via the explicit Hit-link flow) books a court through the Telegram bot exactly as they do today. Nothing about the conversation changes. Days later, without any message being sent to them, Rez creates a payment hold against their saved card. At (or just before) the slot's start time, that hold is captured — the facility gets paid, Rez takes its commission cut, and the player was never interrupted mid-conversation to arrange payment.

**Why this priority**: This is the entire point of Phase 1 — Rez today has no concept of money anywhere. Nothing else in this feature matters if a returning player can't be charged for an ordinary booking with a payment method already on file.

**Independent Test**: Seed a `PlayerPaymentProfile` with a Stripe customer + saved payment method, then run a normal Telegram booking conversation. Verify a hold is created at the reservation's commitment cutoff and captured at the resolution point, with no message sent to the player at either point.

**Acceptance Scenarios**:

1. **Given** a player with a saved payment method already in their `PlayerPaymentProfile`, **When** they book a court through Telegram, **Then** the booking conversation proceeds identically to today — no payment-related prompt appears.
2. **Given** a fulfilled reservation for a player with a payment method on file, **When** the reservation's commitment cutoff (`max(bookingTime, slotStart − commitmentWindow)`) is reached, **Then** a `PaymentEntity` is created in `AUTHORIZED` state, backed by a Stripe `PaymentIntent` on manual capture, confirmed off-session — with no message sent to the player.
3. **Given** an `AUTHORIZED` hold for a reservation that was never cancelled, **When** the resolution point (`slotStart`) is reached, **Then** the hold is captured, the `PaymentEntity` moves to `CAPTURED`, and the facility's share is transferred to their connected Stripe account per the destination-charge/commission split.
4. **Given** a court booked far enough out that the commitment cutoff hasn't arrived yet, **When** the player cancels before that cutoff, **Then** no `PaymentEntity` ever exists for that reservation and cancellation is free, exactly as it is today.

---

### User Story 2 - A first-time player puts a card on file once, at booking time (Priority: P1)

A player with no `PlayerPaymentProfile` yet books a court for the first time. Because there's no native card form in Telegram, the bot sends them a Stripe-hosted link (Checkout in setup mode, or a Payment Link) to enter their card. Once they complete it, Rez learns the result via webhook and their `PlayerPaymentProfile` is populated. Every booking after this one behaves exactly like User Story 1.

**Why this priority**: Equal priority to User Story 1 — without a way to actually collect a card in a Telegram-only conversation, no player can ever reach the "returning player" state at all, so Phase 1 delivers nothing.

**Independent Test**: Start a Telegram booking conversation for a player with no existing `PlayerPaymentProfile`. Verify the bot surfaces a Stripe-hosted card-collection link before the booking is treated as payable, and that completing it (simulated via the corresponding Stripe webhook event) populates `PlayerPaymentProfile` with a `customerId` and `paymentMethodId`.

**Acceptance Scenarios**:

1. **Given** a player with no `PlayerPaymentProfile`, **When** they attempt to book a court, **Then** `CourtBookingWorkflow` surfaces a Stripe-hosted card-collection link before proceeding, instead of silently booking with no payment method.
2. **Given** a player who has completed the Stripe-hosted card-collection flow, **When** `StripeWebhookEndpoint` receives the corresponding `setup_intent.succeeded` / `payment_method.attached` event, **Then** `PlayerPaymentProfile` is created or updated with the resulting Stripe `customerId` and `paymentMethodId`.
3. **Given** a player who already completed the explicit Hit-link flow (out of scope to build here, but assumed possible per `cross-product-identity.md`) before their first Rez booking, **When** they book for the first time on Rez, **Then** no card-collection step is shown — `PlayerPaymentProfile` already resolves to their linked Hit Stripe customer.
4. **Given** a player who starts the card-collection flow but never completes it, **When** they take no further action, **Then** the reservation is not left in a payable-but-uncollectable state indefinitely (see FR-009 / Edge Cases for the exact resolution).

---

### User Story 3 - A facility configures what a slot costs and how much runway players get to cancel for free (Priority: P2)

Before any booking at a facility can be charged, someone needs to set that facility's price per slot, Rez's commission percentage, and the commitment window (how long before a slot's start cancellation stops being free). This is `PricingPolicy`, living on `FacilityEntity` by default, with an optional per-resource override for differently-priced courts.

**Why this priority**: Necessary before User Stories 1/2 can produce a real charge, but it's a one-time-per-facility setup action rather than something that happens on every booking — lower priority than the player-facing booking flow itself.

**Independent Test**: Configure `PricingPolicy` on a `FacilityEntity` (and optionally override it on one `ResourceEntity`), then book that resource and confirm the resulting hold amount and commitment-cutoff timing reflect the override, not the facility default.

**Acceptance Scenarios**:

1. **Given** a facility with no `PricingPolicy` configured, **When** an admin sets price, commission percentage, and commitment window, **Then** subsequent bookings at that facility use those values.
2. **Given** a facility-level `PricingPolicy` and a specific resource with its own override, **When** a court is booked on that resource, **Then** the resource-level override values are used instead of the facility default.
3. **Given** an admin attempts to set a `commitmentWindow` longer than Stripe's authorization ceiling allows for safely, **When** they submit that configuration, **Then** the system rejects it rather than accepting a policy that could silently fail at the commitment cutoff (see FR-011).

---

### User Story 4 - A late-arriving payment failure doesn't leave a court double-booked or a player silently on the hook (Priority: P2)

Payment can fail at two different points, and the system must never let those failures leave a reservation in an ambiguous state: (a) a first-time player never completes the card-collection flow from User Story 2, or (b) a returning player's saved card fails at the commitment cutoff (declined, expired, or requires re-authentication). Both are resolved without leaving a facility's court held indefinitely for a reservation that will never be paid for.

**Why this priority**: Lower priority than the happy paths because it's a failure-recovery concern, but it's still core to Phase 1 being safe to ship — an unresolved payment failure either strands a court or silently exposes the facility to nonpayment.

**Independent Test**: Simulate each failure independently — never completing card setup after booking, and an off-session confirmation that returns `authentication_required` or a decline at the commitment cutoff — and confirm each resolves to one of: reservation cancelled, or player given a bounded grace window with a clear next action, per FR-009/FR-010.

**Acceptance Scenarios**:

1. **Given** a first-time player who never completes the card-collection flow, **When** the grace period defined in FR-009 elapses, **Then** the reservation is cancelled and the court is released.
2. **Given** a returning player whose off-session hold confirmation fails at the commitment cutoff, **When** the failure is `authentication_required` or a decline, **Then** the player is notified with a re-authentication or new-card link and given a bounded grace window before the resolution point (FR-010).
3. **Given** a player notified per the previous scenario, **When** the grace window elapses with no successful hold, **Then** the reservation is cancelled and the court is released — the facility is never left uncertain about whether a slot it's holding will actually be paid for.

### Edge Cases

- What happens when a booking's commitment cutoff computes to a time in the past (e.g. booked the day before the slot, inside an already-active commitment window)? → The hold is created immediately, with no waiting (per the design doc; not a new behavior this spec introduces, but must be preserved).
- What happens when a facility has no Stripe connected account onboarded yet but has a `PricingPolicy` configured? → Booking must not silently succeed into an uncollectable charge; see FR-012.
- What happens when the same player has both a Telegram-only `identity` `userId` and, later, links to an existing Hit account mid-lifecycle of an open (not yet resolved) hold? → Out of scope to resolve automatically; the open hold keeps referencing whichever `PlayerPaymentProfile` existed when it was created (see Out of Scope — account linking/merging).
- What happens if `StripeWebhookEndpoint` receives a `PaymentIntent` lifecycle event for a payment that already resolved (e.g. a duplicate/replayed webhook)? → Must be idempotent; reprocessing a terminal-state event must not double-transfer funds or corrupt `PaymentEntity` state.
- What happens when a facility's `PricingPolicy` changes after a reservation has already been made but before its commitment cutoff? → The reservation is priced using the policy in effect when its commitment-cutoff hold is actually created, not the policy at booking time (see FR-013).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST introduce `PaymentEntity` as an event-sourced component with states `NONE → AUTHORIZED → CAPTURED / VOIDED / REFUNDED / FAILED`, wrapping a Stripe `PaymentIntent` id, amount, currency, the facility's Stripe connected-account id, and Rez's application-fee cut.
- **FR-002**: `ReservationState` MUST gain an optional `paymentId` field referencing the associated `PaymentEntity`, populated once a hold is created for that reservation.
- **FR-003**: System MUST introduce `PricingPolicy` (price per slot, Rez's commission percentage, `commitmentWindow`) as a contract-level default on `FacilityEntity`, with an optional override on a per-`ResourceEntity` basis. A resource-level override MUST take precedence over its facility's default when both exist.
- **FR-004**: System MUST introduce `PlayerPaymentProfile`, mapping a player's canonical `identity` `userId` (already resolved by `TelegramEndpoint` per `001-telegram-identity-resolution`) to a Stripe `customerId` and a default `paymentMethodId`.
- **FR-005**: `CourtBookingWorkflow` MUST check `PlayerPaymentProfile` for the requester's resolved `userId` before submitting a booking. A player with a saved payment method already on file MUST see no different conversational behavior. A player with none MUST be offered a Stripe-hosted card-collection link (Checkout in setup mode, or a Payment Link).
- **FR-006**: System MUST introduce `StripeWebhookEndpoint`, receiving `setup_intent.succeeded` / `payment_method.attached` (to populate `PlayerPaymentProfile`) and `PaymentIntent` lifecycle events (to reconcile `PaymentEntity`). Webhook handling MUST be idempotent against duplicate/replayed events (see Edge Cases).
- **FR-007**: System MUST schedule a commitment-cutoff Timer at `max(bookingTime, slotStart − commitmentWindow)` when a reservation reaches `FULFILLED`. When that Timer fires, System MUST create a `PaymentEntity` with a Stripe `PaymentIntent` on `capture_method: manual`, confirmed off-session against the payment method in the player's `PlayerPaymentProfile`, with no message sent to the player on success.
- **FR-008**: System MUST schedule a resolution-point Timer (default: `slotStart`) when the commitment-cutoff hold is created. When that Timer fires, System MUST capture the hold by default, transferring the facility's share to its connected Stripe account via a destination-charge transfer, consistent with Rez being merchant of record.
- **FR-009**: If a first-time player never completes the card-collection flow from FR-005 within a bounded grace period, System MUST cancel the reservation and release the court. The exact grace period is an operational parameter, not a fixed protocol requirement — see Assumptions.
- **FR-010**: If off-session hold confirmation at the commitment cutoff fails with `authentication_required` or a decline/expired-card error, System MUST notify the player with a re-authentication or new-card link and hold the reservation open for a bounded grace window before the resolution point. If that grace window also elapses without a successful hold, System MUST cancel the reservation and release the court (System MUST NOT fall through to treating this as a paid capture).
- **FR-011**: `PricingPolicy` MUST reject a `commitmentWindow` configuration that would leave a hold open longer than Stripe's authorization capture limit allows for safely, rather than silently accepting a policy that could fail unpredictably at the commitment cutoff.
- **FR-012**: System MUST prevent a booking at a facility from proceeding to a payable state if that facility has a `PricingPolicy` configured but no completed Stripe connected-account onboarding — a price with nowhere to route funds MUST NOT silently produce an uncollectable charge.
- **FR-013**: When a commitment-cutoff hold is created, System MUST use the `PricingPolicy` in effect at that moment (not the policy in effect at original booking time) to determine price and commission.
- **FR-014**: System MUST introduce `SlotPaymentView`, an Akka View keyed by `resourceId + dateTime`, sourced from `PaymentEntity` / `ReservationEntity` events. This feature is responsible only for the view existing and being populated correctly — it defines no consumer of this view (that is Phase 2's responsibility).
- **FR-015**: System MUST NOT implement any rescue-refund logic (voiding a hold because another player books the same slot) or any waiting-list logic (queueing, active-offer exclusivity, priority notification) as part of this feature.

### Key Entities

- **PaymentEntity**: The economic record for a single reservation's payment lifecycle — Stripe `PaymentIntent` id, amount, currency, facility connected-account id, Rez's fee cut, and state (`NONE → AUTHORIZED → CAPTURED / VOIDED / REFUNDED / FAILED`). Exactly one per reservation that reaches a commitment cutoff.
- **PricingPolicy**: Price per slot, Rez's commission percentage, and commitment window, configured per facility with an optional per-resource override.
- **PlayerPaymentProfile**: Minimal mapping from a player's `identity` `userId` to a Stripe `customerId` and default `paymentMethodId`. Does not itself decide whether that `userId` is linked to a Hit account — it simply resolves whatever Stripe customer that `userId` currently maps to.
- **Commitment cutoff**: The computed instant (`max(bookingTime, slotStart − commitmentWindow)`) at which a reservation's hold is created. Not a stored entity — a derived timing value driving a Timer.
- **Resolution point**: The computed instant (default `slotStart`) at which an open hold is captured by default. Not a stored entity — a derived timing value driving a Timer.
- **SlotPaymentView**: A read-side projection keyed by `resourceId + dateTime`, populated in this feature but not yet consumed by anything (Phase 2 will read it for rescue detection).

## Out of Scope

- **Rescue refund (Phase 2)** — voiding a hold when a later booker rescues a cancelled slot before it resolves. Depends entirely on this feature's hold/timer mechanism and `SlotPaymentView`, but the rescue-detection logic itself is not built here.
- **Waiting list (Phase 3)** — `WaitlistEntity`, active-offer exclusivity, priority notification on cancellation. Independent of payments beyond reusing the normal paid-booking path for confirmation; not built here.
- **Explicit cross-product account linking** and **account merging** across a linked identity — both already out of scope per `001-telegram-identity-resolution`, and unaffected by this feature. `PlayerPaymentProfile` keys off whatever `identity` `userId` it's given; it does not implement or depend on the link flow being built.
- **A non-conversational admin surface for configuring `PricingPolicy`** — this spec requires that `PricingPolicy` be configurable (User Story 3) but does not mandate a specific interface. See Assumptions for the default assumed here.
- **Manual invoicing (no Stripe)** as a payment path for a facility partner without Stripe — noted in the design doc as a reasonable bridge but not designed further there, and not built here.
- **Sharing one Stripe account between Hit and Rez** — a separate, open business-entity decision (`hit-backend/docs/implementation-plan.md`). This feature mints its own Stripe customer under a given `userId` unless and until that decision resolves; no migration logic is built here.

## Assumptions

- **PricingPolicy configuration interface** (design doc Open Question #5): configuring `PricingPolicy` is assumed to go through the same admin-only surface already used for other facility-level provisioning in Rez (i.e. a direct API call, not a conversational Telegram command), since a commission/price/commitment-window decision is not something a facility should set by chatting with a booking bot. This is a placeholder default, not a strong design commitment — a dedicated admin UI is explicitly not required.
- **Resolution-point cutoff exactness** (design doc Open Question #2): the resolution point defaults to `slotStart` exactly, with no facility-configurable buffer, for this feature. A configurable pre-`slotStart` buffer is a plausible future enhancement but is not required for Phase 1 to be independently shippable.
- **`commitmentWindow` hard cap** (design doc Open Question #7): FR-011 resolves this as a hard validation cap (rejecting configurations that risk exceeding Stripe's authorization limit) rather than a guideline, since a silently-failing hold at the commitment cutoff is a worse failure mode than a rejected configuration at setup time.
- **Grace periods for FR-009 / FR-010**: this spec requires that bounded grace periods exist and that both failure paths converge on cancel-and-release-the-court as their terminal outcome (per design doc Open Question #8), but treats the exact durations as an operational/configuration detail rather than a protocol-level requirement.
- **Notification channel for FR-010**: assumed to reuse Rez's existing Telegram notification path, consistent with how every other player-facing message in Rez is delivered today.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of bookings by a player with a payment method already on file complete with no visible change to the booking conversation, and produce a captured payment at the resolution point when uncancelled.
- **SC-002**: 100% of first-time bookings surface exactly one card-collection step, and 0% of subsequent bookings by the same player (after completing it) repeat that step.
- **SC-003**: 0% of holds are created before a reservation's commitment cutoff, and 0% of cancellations before that cutoff produce any `PaymentEntity` at all — free cancellation before the commitment window remains completely free.
- **SC-004**: 100% of holds that reach their resolution point without a preceding failure or cancellation are captured, with the facility's share transferred to its connected account.
- **SC-005**: 100% of payment-collection failures (uncompleted card setup, declined/expired card, authentication-required at the commitment cutoff) resolve to a definite outcome — reservation cancelled and court released — within a bounded grace window; none are left in an indefinite or ambiguous state.
- **SC-006**: 0% of duplicate/replayed Stripe webhook events cause a double transfer, a double capture, or a corrupted `PaymentEntity` state.
