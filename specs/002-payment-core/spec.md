# Feature Specification: Payment Core

- **Feature Branch**: `002-payment-core`
- **Created**: 2026-09-05
- **Status**: **Implemented** (2026-09-05). All 59 tasks (T001–T059) complete, `mvn verify` passes
  (unit + integration tests, 29 test classes). Not yet merged to `master`. One caveat: T057's
  quickstart.md walkthrough was only verified in Stripe no-op mode via the automated test suite —
  the full manual pass against a real local stack with live Stripe test-mode credentials was not run
  in this environment (no Stripe test account/credentials available here) and should be done before
  this feature is considered production-ready.
- **Input**: User description: "Start Rez's Payment core — and only Payment core (Phase 1 of rez/docs/payments-cancellation-waitlist-design.md): the Stripe hold anchored to commitment cutoff, PlayerPaymentProfile. Do not scope in Rescue refund or Waiting list — they're Phases 2 and 3 of the same doc, each explicitly sequenced after this one and dependent on it; don't spec or plan them yet. This is now unblocked: Stage 3 of cross-product identity is done — PlayerPaymentProfile can key off the real identity userId Rez's TelegramEndpoint already resolves on every message, no separate Rez sign-in needed."

This spec covers **Phase 1 only** of `docs/payments-cancellation-waitlist-design.md` (§1 "Proposed Model" and "Migration Sequence → Phase 1"). Rescue refund (§2 / Phase 2) and the waiting list (§3 / Phase 3) are explicitly out of scope — both are sequenced after this feature and depend on it; this spec does not design them, beyond noting where Phase 1 must leave room for them (e.g. `SlotPaymentView` existing but having no consumer yet).

The mechanism described in the design doc — hold anchored to the reservation's commitment cutoff, card-on-file collected separately from hold creation, destination charges with Rez as merchant of record — is treated as converged (it went through several rounds of correction; see the doc's Migration Sequence note). This spec does not re-derive that mechanism; it fills in what the doc leaves genuinely open for Phase 1.

## Clarifications

### Session 2026-09-05

- Q: For a first-time player with no card on file, when does the court actually get locked relative to the Stripe card-collection step? → A: Defer booking until card confirmed — `CourtBookingWorkflow` does not submit the reservation at all until `PlayerPaymentProfile` already resolves; nothing is ever locked for an incomplete card flow, matching the design doc's "gains a card-on-file check **before submitting** a booking."
- Q: At the commitment-cutoff Timer, if hold creation fails due to a transient Stripe/network error (not a card decline or auth-required), should the system retry automatically before treating it as a real failure? → A: Retry with bounded backoff before falling into the player-notification/grace-window path — a transient infrastructure hiccup isn't the player's problem.
- Q: Should Phase 1 build `void()`/`refund()` command handlers on `PaymentEntity` now, even though nothing calls them yet? → A: No — declare the full state enum (so Phase 2 needs no migration) but implement command handlers only for the transitions Phase 1 actually drives (`AUTHORIZED → CAPTURED`, `→ FAILED`).
- Q: When should FR-012's Stripe-onboarding-completeness check actually block a booking? → A: At booking time — `CourtBookingWorkflow` rejects upfront if `PricingPolicy` exists but onboarding isn't complete, mirroring the same booking-time gate just established on the player side.

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

### User Story 2 - A first-time player puts a card on file once, before their first court is ever locked (Priority: P1)

A player with no `PlayerPaymentProfile` yet attempts to book a court for the first time. Because there's no native card form in Telegram, and because a court should never be locked for a booking that's doomed to fail payment, `CourtBookingWorkflow` does not submit the reservation at all on this attempt — instead the bot sends them a Stripe-hosted link (Checkout in setup mode, or a Payment Link) and asks them to complete it, then book again. Once they complete it, Rez learns the result via webhook, their `PlayerPaymentProfile` is populated, and their next booking attempt behaves exactly like User Story 1 — nothing was ever locked or held while they were setting up their card.

**Why this priority**: Equal priority to User Story 1 — without a way to actually collect a card in a Telegram-only conversation, no player can ever reach the "returning player" state at all, so Phase 1 delivers nothing.

**Independent Test**: Start a Telegram booking conversation for a player with no existing `PlayerPaymentProfile`. Verify `CourtBookingWorkflow` does not submit a reservation for that attempt (no `ReservationEntity`/resource lock is created) and instead surfaces a Stripe-hosted card-collection link. Then simulate completing the flow via the corresponding Stripe webhook event, confirm `PlayerPaymentProfile` is populated, and verify a subsequent booking attempt succeeds normally.

**Acceptance Scenarios**:

1. **Given** a player with no `PlayerPaymentProfile`, **When** they attempt to book a court, **Then** `CourtBookingWorkflow` does not submit a reservation — no court is locked — and instead surfaces a Stripe-hosted card-collection link.
2. **Given** a player who has completed the Stripe-hosted card-collection flow, **When** `StripeWebhookEndpoint` receives the corresponding `setup_intent.succeeded` / `payment_method.attached` event, **Then** `PlayerPaymentProfile` is created or updated with the resulting Stripe `customerId` and `paymentMethodId`.
3. **Given** a player who already completed the explicit Hit-link flow (out of scope to build here, but assumed possible per `cross-product-identity.md`) before their first Rez booking, **When** they book for the first time on Rez, **Then** no card-collection step is shown — `PlayerPaymentProfile` already resolves to their linked Hit Stripe customer.
4. **Given** a player who starts the card-collection flow but never completes it, **When** they take no further action, **Then** nothing needs to be cleaned up — no reservation, timer, or court lock was ever created for that attempt. They may retry booking at any later time.

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

### User Story 4 - A payment failure at the commitment cutoff never leaves a court held for a slot that won't get paid for (Priority: P2)

A reservation that already exists (the court was locked because the player had a payment method on file at booking time) can still fail to produce a hold at its commitment cutoff — either a transient Stripe/network hiccup, or the saved card itself failing (declined, expired, or requiring re-authentication). The two are handled differently: a transient failure is retried automatically with no player involvement at all; a genuine card failure notifies the player with a bounded grace window before the court is released.

**Why this priority**: Lower priority than the happy paths because it's a failure-recovery concern, but it's still core to Phase 1 being safe to ship — an unresolved payment failure either strands a court or silently exposes the facility to nonpayment.

**Independent Test**: Simulate a transient Stripe/network error at commitment-cutoff hold creation and confirm automatic retry with no player-facing notification. Separately, simulate a genuine decline or `authentication_required` response and confirm the player is notified with a grace window, converging on cancellation if unresolved.

**Acceptance Scenarios**:

1. **Given** a reservation whose commitment-cutoff hold-creation attempt fails with a transient Stripe/network error, **When** the failure occurs, **Then** the system retries automatically with bounded backoff, with no message sent to the player, before treating it as a real failure (FR-016).
2. **Given** a reservation whose retries under FR-016 are exhausted, or whose failure is a genuine card decline/`authentication_required` from the first attempt, **When** that happens, **Then** the player is notified with a re-authentication or new-card link and given a bounded grace window before the resolution point (FR-010).
3. **Given** a player notified per the previous scenario, **When** the grace window elapses with no successful hold, **Then** the reservation is cancelled and the court is released — the facility is never left uncertain about whether a slot it's holding will actually be paid for.

### Edge Cases

- What happens when a first-time player starts the Stripe-hosted card-collection flow but never finishes it? → Nothing needs cleanup — per FR-005/FR-009, no reservation was ever submitted for that attempt. They can simply try booking again whenever they're ready.
- What happens when a booking's commitment cutoff computes to a time in the past (e.g. booked the day before the slot, inside an already-active commitment window)? → The hold is created immediately, with no waiting (per the design doc; not a new behavior this spec introduces, but must be preserved).
- What happens when a facility has no Stripe connected account onboarded yet but has a `PricingPolicy` configured? → Booking must not silently succeed into an uncollectable charge; see FR-012.
- What happens when the same player has both a Telegram-only `identity` `userId` and, later, links to an existing Hit account mid-lifecycle of an open (not yet resolved) hold? → Out of scope to resolve automatically; the open hold keeps referencing whichever `PlayerPaymentProfile` existed when it was created (see Out of Scope — account linking/merging).
- What happens if `StripeWebhookEndpoint` receives a `PaymentIntent` lifecycle event for a payment that already resolved (e.g. a duplicate/replayed webhook)? → Must be idempotent; reprocessing a terminal-state event must not double-transfer funds or corrupt `PaymentEntity` state.
- What happens when a facility's `PricingPolicy` changes after a reservation has already been made but before its commitment cutoff? → The reservation is priced using the policy in effect when its commitment-cutoff hold is actually created, not the policy at booking time (see FR-013).
- What happens when a booking is created via `BookingEndpoint`'s direct HTTP API instead of the Telegram/agent path? → FR-012's facility-onboarding gate still applies (facility is derivable from `resourceIds` either way). FR-005's player-card-on-file gate does **not** apply — `BookingEndpoint`'s request shape carries no player-identity concept at all (its `Init` call already hardcodes `identityUserId` to empty, per `001-telegram-identity-resolution`), so there is no `PlayerPaymentProfile` to check against. A booking made this way can reach `FULFILLED` and still fail to produce a hold at its commitment cutoff for lack of any payment method — which resolves via the ordinary FR-010 failure path (notify has no channel without a resolved identity, so it converges directly on cancellation once the grace window elapses). See Out of Scope.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST introduce `PaymentEntity` as an event-sourced component with states `NONE → AUTHORIZED → CAPTURED / VOIDED / REFUNDED / FAILED`, wrapping a Stripe `PaymentIntent` id, amount, currency, the facility's Stripe connected-account id, and Rez's application-fee cut.
- **FR-002**: `ReservationState` MUST gain an optional `paymentId` field referencing the associated `PaymentEntity`, populated once commitment-cutoff processing begins for that reservation — not only once a hold is successfully authorized. A `PaymentEntity` that later ends in `FAILED` without ever reaching `AUTHORIZED` still needs a `paymentId` to be recorded against, so this is deliberately earlier than "a hold exists."
- **FR-003**: System MUST introduce `PricingPolicy` (price per slot, Rez's commission percentage, `commitmentWindow`) as a contract-level default on `FacilityEntity`, with an optional override on a per-`ResourceEntity` basis. A resource-level override MUST take precedence over its facility's default when both exist.
- **FR-004**: System MUST introduce `PlayerPaymentProfile`, mapping a player's canonical `identity` `userId` (already resolved by `TelegramEndpoint` per `001-telegram-identity-resolution`) to a Stripe `customerId` and a default `paymentMethodId`.
- **FR-005**: `CourtBookingWorkflow` MUST check `PlayerPaymentProfile` for the requester's resolved `userId` before submitting a booking, for every booking-creation entry point that carries a resolved player identity (the Telegram/agent path, per `001-telegram-identity-resolution`). A player with a saved payment method already on file MUST see no different conversational behavior. A player with none MUST NOT have a reservation submitted on their behalf — `CourtBookingWorkflow` MUST instead surface a Stripe-hosted card-collection link (Checkout in setup mode, or a Payment Link) and require them to retry booking once it's completed. This gate does not apply to `BookingEndpoint`'s direct HTTP path, which carries no player-identity concept today — see FR-012 and Out of Scope.
- **FR-006**: System MUST introduce `StripeWebhookEndpoint`, receiving `setup_intent.succeeded` / `payment_method.attached` (to populate `PlayerPaymentProfile`) and `PaymentIntent` lifecycle events (to reconcile `PaymentEntity`). Webhook handling MUST be idempotent against duplicate/replayed events (see Edge Cases).
- **FR-007**: System MUST schedule a commitment-cutoff Timer at `max(bookingTime, slotStart − commitmentWindow)` when a reservation reaches `FULFILLED`. When that Timer fires, System MUST create a `PaymentEntity` with a Stripe `PaymentIntent` on `capture_method: manual`, confirmed off-session against the payment method in the player's `PlayerPaymentProfile`, with no message sent to the player on success.
- **FR-008**: System MUST schedule a resolution-point Timer (default: `slotStart`) when the commitment-cutoff hold is created. When that Timer fires, System MUST capture the hold by default, transferring the facility's share to its connected Stripe account via a destination-charge transfer, consistent with Rez being merchant of record.
- **FR-009**: If a first-time player never completes the card-collection flow from FR-005, System MUST NOT have created a reservation for that booking attempt in the first place — since none was ever submitted, no timeout, cleanup, or cancellation logic is needed for this path. The player may retry booking at any later time, at which point FR-005's check re-evaluates normally.
- **FR-010**: If off-session hold confirmation at the commitment cutoff fails with a genuine card-specific error (`authentication_required`, decline, or expired card) — or with a transient error whose retries under FR-016 are exhausted — System MUST notify the player with a re-authentication or new-card link and hold the reservation open for a bounded grace window before the resolution point. If that grace window also elapses without a successful hold, System MUST cancel the reservation and release the court (System MUST NOT fall through to treating this as a paid capture).
- **FR-011**: `PricingPolicy` MUST reject a `commitmentWindow` configuration that would leave a hold open longer than Stripe's authorization capture limit allows for safely, rather than silently accepting a policy that could fail unpredictably at the commitment cutoff.
- **FR-012**: Every booking-creation entry point — `CourtBookingWorkflow` (Telegram/agent path) **and** `BookingEndpoint` (direct HTTP path) — MUST reject a booking at submission time, not merely at the commitment cutoff, if the target facility (resolved from the requested resource(s)) has a `PricingPolicy` configured but no completed Stripe connected-account onboarding. Unlike FR-005, this gate needs no player identity — only the facility, which is derivable from `resourceIds` on both paths — so it MUST apply uniformly to both. A price with nowhere to route funds MUST NOT be allowed to reach a payable reservation state, regardless of which entry point created it.
- **FR-013**: When a commitment-cutoff hold is created, System MUST use the `PricingPolicy` in effect at that moment (not the policy in effect at original booking time) to determine price and commission.
- **FR-014**: System MUST introduce `SlotPaymentView`, an Akka View keyed by `resourceId + dateTime`, sourced from `PaymentEntity` / `ReservationEntity` events. This feature is responsible only for the view existing and being populated correctly — it defines no consumer of this view (that is Phase 2's responsibility).
- **FR-015**: System MUST NOT implement any rescue-refund logic (voiding a hold because another player books the same slot) or any waiting-list logic (queueing, active-offer exclusivity, priority notification) as part of this feature.
- **FR-016**: When a commitment-cutoff hold-creation attempt fails due to a transient error (e.g. Stripe API/network unavailability) rather than a card-specific decline, System MUST retry automatically with bounded backoff, with no message sent to the player during these retries. Only once retries are exhausted, or the failure is card-specific from the first attempt, does FR-010's player-notification path apply.
- **FR-017**: `PaymentEntity` MUST declare its full state enum (`NONE → AUTHORIZED → CAPTURED / VOIDED / REFUNDED / FAILED`) per the design doc, but this feature MUST implement command handlers only for the transitions it actually drives: `AUTHORIZED → CAPTURED` (at the resolution point) and `→ FAILED` (on exhausted hold-creation failure per FR-016/FR-010). Command handlers for `VOIDED` and `REFUNDED` are explicitly not built in this feature — they are reserved for Phase 2 (rescue refund) and a future admin/dispute-refund capability, respectively.

### Key Entities

- **PaymentEntity**: The economic record for a single reservation's payment lifecycle — Stripe `PaymentIntent` id, amount, currency, facility connected-account id, Rez's fee cut, and state (`NONE → AUTHORIZED → CAPTURED / VOIDED / REFUNDED / FAILED`). Exactly one per reservation that reaches a commitment cutoff. This feature drives only `AUTHORIZED → CAPTURED` and `→ FAILED`; `VOIDED`/`REFUNDED` are declared but not yet reachable (see FR-017).
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
- **A player-identity concept for `BookingEndpoint`'s direct HTTP API** — `BookingEndpoint.BookingRequest` carries no player/customer identity field today (its `Init` call already hardcodes `identityUserId` to `Optional.empty()`, predating this feature). Without one, FR-005's player-side gate has nothing to check there. Adding a payer-identity concept to that API is a real, pre-existing gap this feature does not create and does not resolve — it's tracked here as a named limitation rather than silently left uncovered. FR-012's facility-side gate, which needs no player identity, does still apply to this entry point (see FR-012).

## Assumptions

- **PricingPolicy configuration interface** (design doc Open Question #5): configuring `PricingPolicy` is assumed to go through the same admin-only surface already used for other facility-level provisioning in Rez (i.e. a direct API call, not a conversational Telegram command), since a commission/price/commitment-window decision is not something a facility should set by chatting with a booking bot. This is a placeholder default, not a strong design commitment — a dedicated admin UI is explicitly not required.
- **Resolution-point cutoff exactness** (design doc Open Question #2): the resolution point defaults to `slotStart` exactly, with no facility-configurable buffer, for this feature. A configurable pre-`slotStart` buffer is a plausible future enhancement but is not required for Phase 1 to be independently shippable.
- **`commitmentWindow` hard cap** (design doc Open Question #7): FR-011 resolves this as a hard validation cap (rejecting configurations that risk exceeding Stripe's authorization limit) rather than a guideline, since a silently-failing hold at the commitment cutoff is a worse failure mode than a rejected configuration at setup time.
- **Grace period for FR-010, and retry count for FR-016**: this spec requires that a bounded grace period exists and that the hold-creation failure path converges on cancel-and-release-the-court as its terminal outcome (per design doc Open Question #8), and that transient failures get a bounded number of automatic retries first (FR-016) — but treats the exact durations/counts as an operational/configuration detail rather than a protocol-level requirement.
- **Notification channel for FR-010**: assumed to reuse Rez's existing Telegram notification path, consistent with how every other player-facing message in Rez is delivered today.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of bookings by a player with a payment method already on file complete with no visible change to the booking conversation, and produce a captured payment at the resolution point when uncancelled.
- **SC-002**: 100% of first-time booking attempts surface exactly one card-collection step and result in no reservation being created; 0% of subsequent booking attempts by the same player (after completing it) repeat that step or are blocked.
- **SC-003**: 0% of holds are created before a reservation's commitment cutoff, and 0% of cancellations before that cutoff produce any `PaymentEntity` at all — free cancellation before the commitment window remains completely free.
- **SC-004**: 100% of holds that reach their resolution point without a preceding failure or cancellation are captured, with the facility's share transferred to its connected account.
- **SC-005**: 100% of commitment-cutoff hold-creation failures (declined/expired card, authentication-required, or a transient error with retries exhausted) resolve to a definite outcome — reservation cancelled and court released — within a bounded grace window; none are left in an indefinite or ambiguous state.
- **SC-006**: 0% of duplicate/replayed Stripe webhook events cause a double transfer, a double capture, or a corrupted `PaymentEntity` state.
- **SC-007**: 0% of booking attempts, on the Telegram/agent path, by a player without a completed card-on-file ever result in a locked court (FR-005/FR-009). 0% of booking attempts, on **either** entry point (Telegram/agent or `BookingEndpoint`'s direct HTTP path), at a facility without completed Stripe onboarding ever result in a locked court (FR-012).
- **SC-008**: 0% of commitment-cutoff hold-creation failures caused purely by a transient Stripe/network error reach the player-notification path without first being retried per FR-016.
