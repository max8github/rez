# Research: Payment Core

Phase 0 output for `002-payment-core`. Each item below was a genuine unknown after reading the design
doc (`docs/payments-cancellation-waitlist-design.md` §1) against the actual current codebase — not
re-litigating anything the design doc or spec.md's Clarifications already settled.

## 1. Which Maven module hosts the new components?

**Decision**: All new payment components live inside the existing `reservation` module
(`reservation/reservation/`), in a new `com.rezhub.reservation.payment` package (domain + application),
plus one new `com.rezhub.reservation.infrastructure.StripeService` class and one new
`com.rezhub.reservation.api.StripeWebhookEndpoint`. No new Maven module.

**Rationale**: The design doc's own Code Mapping section says exactly this ("new package, e.g.
`com.rezhub.reservation.payment`"). `PaymentEntity` needs tight coordination with `ReservationEntity`
and `CourtBookingWorkflow`, both already in this module; splitting it into a separate module would add
a cross-module dependency for no isolation benefit, since nothing else in the repo needs to consume
payment components independently of the reservation module. `spi`/`telegramnotifier`/`notifierstub`
are separate modules because they're a genuinely swappable interface/implementation pair (notification
transport) — Stripe has no equivalent swap requirement here.

**Alternatives considered**: A new top-level `payments` Maven module (sibling to `reservation`, `spi`,
etc.) — rejected as premature separation; nothing in this feature needs `payment` code deployed or
versioned independently of `reservation`.

## 2. How does `PlayerPaymentProfile` fit the existing component taxonomy?

**Decision**: `PlayerPaymentProfile` is a Key Value Entity (`com.rezhub.reservation.payment.PlayerPaymentProfileEntity`), keyed by the `identity` `userId`.

**Rationale**: Per AGENTS.md's component guidance, a Key Value Entity is the right fit for "simpler
than Event Sourced — direct state updates" data with no interesting event history of its own. A
player's Stripe `customerId`/`paymentMethodId` mapping has no lifecycle worth event-sourcing — it's
just current-value lookup data, structurally identical to how `hit-backend`'s `Player.stripeCustomerId`
field is just a plain field, not its own event stream. Keying by `userId` (not a Rez-local id) is
required by FR-004 and matches `cross-product-identity.md`'s decision to key by the shared `identity`
`userId` from day one.

**Alternatives considered**: Event Sourced Entity — rejected; no audit-trail requirement exists for
this data (unlike `PaymentEntity`, which genuinely needs an event history for the financial state
machine).

## 3. `PaymentEntity` identity — random UUID vs. deterministic from `reservationId`?

**Decision**: `PaymentEntity`'s entity id equals its owning `ReservationEntity`'s `reservationId`
(1:1, deterministic) — no separate `paymentId` generation step.

**Rationale**: Per Key Entities in spec.md, exactly one `PaymentEntity` exists per reservation that
reaches a commitment cutoff. Generating a random `paymentId` would require persisting it back onto
`ReservationState` before it's usable (an extra round trip, and a re-entrancy question: what if the
commitment-cutoff `TimedAction` crashes after generating an id but before persisting it back?).
Reusing `reservationId` directly sidesteps that: `ReservationState.paymentId` can simply be set to
`Optional.of(reservationId)` at the moment the commitment-cutoff Timer fires (before any Stripe call is
attempted), and the `PaymentEntity` for a given reservation is always addressable by that same id with
no lookup needed. This also makes FR-002's "populated once a hold is created" read most naturally as
"populated once commitment-cutoff processing begins" rather than "populated only after `AUTHORIZED` is
reached" — a `PaymentEntity` that only gets created after success would have nowhere to record retry
attempts (FR-016) or a `FAILED` outcome for a hold that never actually authorized.

**Alternatives considered**: Random UUID `paymentId` stored back onto `ReservationState` after
successful authorization only — rejected per the reasoning above (no place to represent a
never-authorized `FAILED` outcome, and an extra unnecessary round trip).

## 4. `PaymentEntity`'s state machine — is `FAILED` only reachable from `AUTHORIZED`?

**Decision**: `FAILED` is reachable directly from `NONE` (a hold-creation attempt that never reaches
`AUTHORIZED` — the design doc's own "Known gap... off-session confirmation can occasionally fail with
`authentication_required`" describes exactly this), in addition to being conceptually part of the
`AUTHORIZED → {CAPTURED, VOIDED, REFUNDED, FAILED}` set of terminal outcomes the design doc's shorthand
`NONE → AUTHORIZED → CAPTURED / VOIDED / REFUNDED / FAILED` describes. This feature's Phase 1 command
surface (per FR-017) only ever drives `NONE → AUTHORIZED`, `AUTHORIZED → CAPTURED`, and `{NONE,
AUTHORIZED} → FAILED` — never `VOIDED`/`REFUNDED`.

**Rationale**: The design doc's arrow notation is a lifecycle summary, not a strict FSM diagram; its
own prose (§1 "Known gap") describes exactly a NONE→FAILED path. Treating `FAILED` as reachable only
from `AUTHORIZED` would leave no way to represent FR-010's "off-session hold confirmation... fails"
outcome, which by definition never reached `AUTHORIZED`.

**Alternatives considered**: A separate `PENDING`/`CREATING` intermediate state between `NONE` and
`AUTHORIZED` to make the FSM diagram literal — rejected as unneeded complexity; nothing in Phase 1
needs to distinguish "hold creation is in flight" from "no hold exists yet" as separate *persisted*
states, since retries (FR-016) are tracked as a Timer-driven attempt counter, not entity state.

## 5. Where does the commitment-cutoff Timer get scheduled from?

**Decision**: A new Consumer, `PaymentSchedulingAction` (`com.rezhub.reservation.payment` package),
subscribed to `ReservationEntity`'s `Fulfilled` event (same subscription style as the existing
`DelegatingServiceAction`), computes the commitment cutoff and schedules a single Akka Timer via
`TimerScheduler`, deferring to a new `com.rezhub.reservation.payment.CommitmentCutoffTimedAction`.

**Rationale**: This mirrors the exact existing pattern for reacting to `Fulfilled`
(`DelegatingServiceAction.on(ReservationEvent.Fulfilled)`), which already resolves the resource and
sends a notification — adding a second, independent consumer of the same event for payment scheduling
keeps the two concerns (notification vs. payment) in separate, single-responsibility components rather
than growing `DelegatingServiceAction` into a payment-aware class. `TimedAction` is the SDK's
documented mechanism for scheduled/delayed work (AGENTS.md's Timed Action pattern), and computing "when
to fire" (the commitment cutoff, and later the resolution point) as a plain duration passed to
`TimerScheduler.createSingleTimer` needs no new persisted state beyond what `PaymentEntity` and
`ReservationState` already carry.

**Alternatives considered**: Scheduling the Timer directly inside `CourtBookingWorkflow.book()` at
submission time — rejected because the commitment cutoff must be computed from the *fulfilled*
reservation's actual locked slot (`Fulfilled` carries the real `resourceId`/`dateTime` the reservation
ended up with), and a reservation that fails to fulfill (rejected candidates, `SearchExhausted`) must
never get a payment Timer at all. Reacting to `Fulfilled` — not `book()`'s call site — makes "no
successful booking, no Timer" true by construction rather than by an extra guard.

## 6. Retry-with-backoff mechanics for FR-016 — where does attempt-count state live?

**Decision**: The attempt count is a parameter threaded through the `TimedAction`'s own deferred-call
argument, not persisted entity state. `CommitmentCutoffTimedAction.attemptHold(PaymentEntity.HoldAttempt
command)` (carrying `reservationId` and `attemptNumber`) calls `StripeService`; on a transient failure
it calls `TimerScheduler.createSingleTimer` again with a backoff delay and `attemptNumber + 1`; on
success it invokes `PaymentEntity::authorize`; on a genuine card-specific failure, or once
`attemptNumber` reaches a configured max, it invokes `PaymentEntity::fail` and proceeds to FR-010's
notification/grace-window path (a further Timer for the grace window, same mechanism).

**Rationale**: `TimedAction` methods are stateless per AGENTS.md ("Stateless, methods return
`Effect<Done>`"); Akka's own Timer mechanism already durably tracks "a timer is scheduled to fire," so
there's no need for `PaymentEntity` to separately track retry count as persisted state — doing so would
mean every retry both re-fires a Timer *and* persists an event, double-bookkeeping the same fact for no
benefit. `PaymentEntity`'s event log records only externally-meaningful transitions (`AUTHORIZED`,
`CAPTURED`, `FAILED`), matching the constitution's Simplicity principle (build only what's needed) and
matching `DebriefTimeoutAction`/`SessionTimeoutAction`'s existing precedent in `hit-backend`, which are
similarly stateless w.r.t. their own retry/timeout bookkeeping.

**Alternatives considered**: Persisting attempt count as a field on `PaymentState` — rejected as
unnecessary; would require a new event type per retry attempt purely for bookkeeping the SDK's own
Timer mechanism already handles.

## 7. Stripe integration shape — reusing `hit-backend`'s `StripeService` pattern

**Decision**: A new `com.rezhub.reservation.infrastructure.StripeService` class in the `reservation`
module, structurally modeled on `hit-backend`'s `hit.infrastructure.StripeService` (same no-op-mode
pattern when `STRIPE_SECRET_KEY` is absent, same idempotency-key discipline, same
`createTransferFromCharge`/`createConnectAccount`/`createAccountLink`/`isConnectAccountChargesEnabled`
shapes) — not a shared library, since Rez and Hit are separate repos/services with (per
`cross-product-identity.md`) potentially separate Stripe accounts. One material difference from Hit's
version: Hit's `createPaymentIntent` relies on a *client-side* confirm (mobile PaymentSheet) after
creation — Rez has no mobile client in this flow at all, so its hold creation must call Stripe with
`.setCustomer(customerId).setPaymentMethod(paymentMethodId).setOffSession(true).setConfirm(true)` in
the same server-side call, fully unattended, matching FR-007's "confirmed off-session" requirement with
no separate confirmation step.

**Rationale**: Duplicating the class (rather than extracting a shared library across two separate git
repos) matches this codebase's existing convention — `IdentityClient` is independently reimplemented
in both `hit-backend` and `rez` (its own doc comment says "Mirrors hit-backend's
`hit.infrastructure.IdentityClient`") rather than shared, and the constitution's Simplicity principle
favors not introducing a new shared-library dependency/build/versioning burden for one class family.

**Alternatives considered**: A shared `stripe-common` library — rejected; no precedent in this
codebase for sharing code across the `hit-backend`/`rez` repo boundary, and the two services'
integration surfaces already differ in the way described above (client-confirmed vs. fully
off-session), so a shared abstraction would need feature flags from day one.

**Dependency to add**: `com.stripe:stripe-java:27.1.0` (same pinned version `hit-backend` already
uses) to `reservation/reservation/pom.xml`. Per this project's Maven-versioning convention (fixed
versions only, no `SNAPSHOT`, a dependency addition bumps `reservation`'s own module version, pom
changes committed separately from code), the `reservation` module's version in
`reservation/reservation/pom.xml` gets bumped as part of this feature's implementation, in its own
commit.

## 8. Admin surface for `PricingPolicy` configuration (spec Assumption)

**Decision**: New `PUT` endpoints on the existing `FacilityEndpoint` (`PUT /facility/{facilityId}/pricing-policy`) and `ResourceEndpoint` (`PUT /resource/{resourceId}/pricing-policy` for the override), following the exact existing pattern (`PUT /facility/{facilityId}/address`, `PUT /facility/{facilityId}/name`) — a direct API call, no Telegram command, no dedicated admin UI.

**Rationale**: Matches spec.md's Assumption directly, and reuses `FacilityEndpoint`'s established
`@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))` + direct `PUT` pattern rather than
inventing a new endpoint shape.

## 9. Where does the FR-012 (facility onboarding) and FR-005 (player card-on-file) booking-time gate live?

**Decision**: Both checks are added as the first steps of `CourtBookingWorkflow.book()`, before it
resolves scope / calls `reservationGateway.submit()` — returning a new sealed result type (e.g.
`BookingHandle` = `ReservationHandle` | `CardSetupRequired` | `FacilityNotPayable`) instead of `book()`'s
current bare `ReservationHandle` return type, so `BookingTools.bookCourt()` can surface a distinct
message for each case instead of always returning `"BOOKING_SUBMITTED:" + id`.

**Rationale**: `book()` is the single existing chokepoint every booking path already goes through
(`BookingApplicationService` → `CourtBookingWorkflow.book()`), so gating there (rather than duplicating
the check in `BookingTools` and `BookingEndpoint` separately) guarantees FR-005/FR-012 apply uniformly
regardless of entry point (Telegram agent path or direct `POST /bookings`), matching how the design doc
itself scopes the check to `CourtBookingWorkflow` ("gains a card-on-file check before submitting a
booking").

**Alternatives considered**: Gating inside `BookingTools.bookCourt()` only — rejected because
`BookingEndpoint`'s direct `POST /bookings` path would then bypass both checks entirely, silently
producing an unpayable reservation for any non-Telegram caller.

**Correction (found during `/akka.analyze`, 2026-09-05)**: the reasoning above is still right about
*why* to gate at a single chokepoint, but wrong about *which* chokepoint covers every path.
`docs/reference/rez-system-overview.md`'s architecture diagram — and `BookingEndpoint.java` itself,
read directly to confirm — show that `BookingEndpoint` does **not** go through
`CourtBookingWorkflow.book()` at all; it calls `ReservationEntity::init` directly. Gating only inside
`book()` leaves `BookingEndpoint` fully uncovered, which is exactly the failure mode this decision's
own "Alternatives considered" paragraph warned against — just at one chokepoint further out than
originally traced. See #10 below for the corrected design.

## 10. Closing the `BookingEndpoint` gap: a shared `PaymentGate`, split by what each check actually needs

**Decision**: Extract FR-005/FR-012's two checks into a small, stateless `PaymentGate`
(`com.rezhub.reservation.payment.PaymentGate`) with two independent methods —
`isPlayerPayable(Optional<String> identityUserId)` and `isFacilityPayable(String facilityId)` — and
call both from `CourtBookingWorkflow.book()` (Telegram/agent path), but call only
`isFacilityPayable` from `BookingEndpoint.book()` (direct HTTP path). `BookingEndpoint` does not call
`isPlayerPayable` because `BookingRequest` has nothing to pass it — confirmed by reading
`BookingEndpoint.java`: `Init` is constructed with `identityUserId` hardcoded to `Optional.empty()`
already, predating this feature.

**Rationale**: FR-012's check needs only a facility id, which `BookingEndpoint` can derive from its
`resourceIds` (via the same `ResourcesByFacilityView` lookup `CourtDirectoryAkka` already uses) exactly
as reliably as the Telegram path can — there's no reason to leave facility-onboarding unenforced there.
FR-005's check needs a resolved player identity, which simply doesn't exist on this entry point's
request shape; inventing one (e.g. requiring a `userId` field on `BookingRequest`) would be a new
API-surface decision for `BookingEndpoint`'s existing non-AI callers, well beyond this feature's scope.
Splitting the gate into two independently-callable checks, rather than one combined
"is this booking payable" method, makes this asymmetry explicit in the code rather than requiring a
caller to pass a fake/empty identity through a combined check.

**Consequence for FR-010's failure path**: a `BookingEndpoint`-created reservation that fails to
produce a hold at its commitment cutoff has no resolved identity to notify (FR-010's "notify the
player" step has no channel). It still converges on cancellation once the grace window elapses — the
notification step is simply a no-op for this path, not a different terminal outcome. No new FR needed;
FR-010 already says "notify... and hold open for a bounded grace window... if that grace window also
elapses without a successful hold, cancel" — the notify sub-step degrading to a no-op when there's
no recipient doesn't change the cancellation guarantee.

**Alternatives considered**: (1) Route `BookingEndpoint` through `BookingApplicationService`/
`CourtBookingWorkflow` instead of calling `ReservationEntity::init` directly — rejected as a much
larger architectural change (redesigning a working, documented external API's internal call path) than
this feature should take on as a side effect of adding payments. (2) Add a player-identity field to
`BookingRequest` so `BookingEndpoint` could also enforce FR-005 — rejected for the same reason: that's
a real, separate API-design decision (who are `BookingEndpoint`'s non-AI callers, and what identity do
they authenticate as?) that this feature shouldn't quietly make as a side effect.
