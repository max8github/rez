# Tasks: Payment Core

**Input**: Design documents from `specs/002-payment-core/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/payment-api.md, quickstart.md (all present)

**Tests**: Included. Not optional for this project — the constitution's Test Coverage principle
("every behavioral change MUST be accompanied by tests") makes this a hard requirement.

**Organization**: Tasks are grouped by user story. US1 and US2 are both P1 and share one code unit by
design — `CourtBookingWorkflow.book()`'s FR-005 gate is a single if/else that can't be meaningfully
half-built, so US1's phase implements the whole gate (both branches) and US2's phase adds the
card-collection machinery the "no profile" branch surfaces. This mirrors how spec 001's US1/US3 shared
code by design; noted explicitly here for the same reason — it's intentional, not a scope leak.

**Revised after `/akka.analyze`** (2026-09-05): that review found `BookingEndpoint`'s direct HTTP path
bypasses `CourtBookingWorkflow` entirely and has no player-identity concept at all (confirmed by
reading `BookingEndpoint.java`). Both booking-time checks (FR-005, FR-012) now go through a shared
`PaymentGate` (Foundational, T016) instead of living inline in `CourtBookingWorkflow`, so
`BookingEndpoint` can call the one check that applies to it (`isFacilityPayable`, FR-012) without
duplicating logic. Also added: a `SlotPaymentView` test (Foundational), a webhook-replay idempotency
test (US2), and a `PricingPolicy`-changed-mid-flight test (US1) — all previously uncovered by any task.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Maps the task to a user story from spec.md (US1, US2, US3, US4)
- File paths are relative to `/Users/max/code/rez/` unless given as absolute paths

---

## Phase 1: Setup

- [X] T001 Add `com.stripe:stripe-java:27.1.0` dependency to
      `reservation/reservation/pom.xml`; bump the `reservation` module's own version per this
      project's Maven-versioning convention (fixed versions only, pom change committed separately
      from code — see `feedback_maven_versioning`); confirm `mvn -o compile` still succeeds from
      `reservation/reservation/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The new domain types, entities, and infrastructure client every user story depends on.
Nothing here is itself user-visible — no component is wired into the booking flow yet. Must compile
and pass its own unit tests before any story-specific work begins.

- [X] T002 [P] Create `PricingPolicy` domain record (`priceCents`, `currency`, `commissionFraction`,
      `commitmentWindow: Duration`) with a `validate()` method enforcing FR-011's Stripe-safe cap, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PricingPolicy.java`
- [X] T003 [P] Add `Optional<PricingPolicy> pricingPolicy` and `Optional<String>
      stripeConnectedAccountId` fields to `FacilityState`, with `withPricingPolicy(...)` and
      `withStripeConnectedAccountId(...)` builder methods following the existing `with*` pattern, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityState.java`
      (depends on T002)
- [X] T004 Add `PricingPolicySet(String facilityId, PricingPolicy policy)` and
      `StripeConnectedAccountSet(String facilityId, String accountId)` variants to `FacilityEvent`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityEvent.java`
      (depends on T002)
- [X] T005 Add `setPricingPolicy(PricingPolicy)` and `setStripeConnectedAccount(String)` command
      handlers to `FacilityEntity`, persisting the two new events and updating `applyEvent`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityEntity.java`
      (depends on T003, T004)
- [X] T006 [P] Add `Optional<PricingPolicy> pricingPolicyOverride` field to `ResourceState`, with a
      `withPricingPolicyOverride(...)` builder method following the existing `with*` pattern, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java`
      (depends on T002)
- [X] T007 Add `PricingPolicyOverrideSet(String resourceId, PricingPolicy policy)` variant to
      `ResourceEvent`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEvent.java`
      (depends on T002)
- [X] T008 Add `setPricingPolicyOverride(PricingPolicy)` command handler to `ResourceEntity`,
      persisting the new event and updating `applyEvent`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java`
      (depends on T006, T007)
- [X] T009 [P] Add `Optional<String> paymentId` field to `ReservationState`, defaulting to
      `Optional.empty()` in `initiate()`, with a `withPaymentId(Optional<String>)` builder method and
      the field appended to every existing `with*` method's positional reconstruction, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationState.java`
- [X] T010 Add a `PaymentIdRecorded(String paymentId)` event and a corresponding
      `recordPaymentId(String paymentId)` command handler to `ReservationEntity` (valid only from
      `FULFILLED`), applying it via the new `withPaymentId` builder — see plan.md's "deliberate design
      note" for why this is a separate command rather than a field on `Fulfilled` itself, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java`
      and `.../ReservationEvent.java` (depends on T009)
- [X] T011 [P] Create `PaymentState` (record + `State` enum: `NONE, AUTHORIZED, CAPTURED, VOIDED,
      REFUNDED, FAILED`) and `PaymentEvent` (sealed interface: `HoldAuthorized`, `HoldCaptured`,
      `HoldCreationFailed`, each `@TypeName`-annotated) per data-model.md, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PaymentState.java` and
      `.../PaymentEvent.java`
- [X] T012 Create `PaymentEntity` (Event Sourced) with `authorize(Authorize)`, `capture(Capture)`, and
      `fail(Fail)` command handlers per data-model.md's state-transition rules (each rejects from an
      invalid source state — see data-model.md's idempotency-guard note); **do not** add `void()` or
      `refund()` handlers (FR-017), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PaymentEntity.java`
      (depends on T011)
- [X] T013 [P] Create `PlayerPaymentProfileState` record (`userId`, `Optional<String>
      stripeCustomerId`, `Optional<String> defaultPaymentMethodId`, `hasPaymentMethod()`), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PlayerPaymentProfileState.java`
- [X] T014 Create `PlayerPaymentProfileEntity` (Key Value Entity) with `linkCustomer(String)` and
      `setDefaultPaymentMethod(String)` command handlers, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PlayerPaymentProfileEntity.java`
      (depends on T013)
- [X] T015 [P] Create `StripeService` in
      `reservation/reservation/src/main/java/com/rezhub/reservation/infrastructure/StripeService.java`,
      modeled on `hit-backend`'s (research.md #7): no-op mode when `STRIPE_SECRET_KEY` is absent,
      idempotency keys on every mutating call. Methods needed by this feature: off-session
      `createAndConfirmHold(long amountCents, String currency, String customerId, String
      paymentMethodId, String idempotencyKey)` (unlike Hit's client-confirmed
      `createPaymentIntent`, this one sets `.setCustomer().setPaymentMethod().setOffSession(true).setConfirm(true)`
      in one call — see research.md #7), `capturePaymentIntent`, `createTransferFromCharge`,
      `createCustomer`, `createConnectAccount`, `isConnectAccountChargesEnabled` (depends on T001)
- [X] T016 Create `PaymentGate` (`com.rezhub.reservation.payment.PaymentGate`), a small stateless
      helper with two independent methods: `isPlayerPayable(Optional<String> identityUserId)` (queries
      `PlayerPaymentProfileEntity` — `Optional.empty()` in ⇒ not payable, matching "no identity, no
      profile to check") and `isFacilityPayable(String facilityId)` (queries `FacilityEntity` for a
      `PricingPolicy` present alongside a Stripe connected-account id that
      `StripeService.isConnectAccountChargesEnabled` confirms is active). Kept as two independent
      methods, not one combined check, because `BookingEndpoint` (T049) can only ever call the second
      one — see research.md #10, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PaymentGate.java` (depends
      on T005, T014, T015)
- [X] T017 Create `SlotPaymentView`, keyed by `resourceId + dateTime`, sourced from
      `PaymentEvent.HoldAuthorized`/`HoldCaptured`/`HoldCreationFailed` and
      `ReservationEvent.Fulfilled` (for the `resourceId + dateTime` key itself — see data-model.md),
      in `reservation/reservation/src/main/java/com/rezhub/reservation/payment/SlotPaymentView.java`
      (depends on T012)
- [X] T018 [P] Add `SlotPaymentViewTest` (`Awaitility`-based, matching this project's existing View-test
      convention): publish `Fulfilled` then `HoldAuthorized`/`HoldCaptured` events directly and assert
      the view becomes queryable by `resourceId + dateTime` with the expected payment state, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/SlotPaymentViewTest.java`
      (depends on T017)
- [X] T019 [P] Add `PaymentEntityTest` (EventSourcedTestKit) covering: `authorize` from `NONE`
      succeeds; `capture` from `AUTHORIZED` succeeds; `fail` from `NONE` and from `AUTHORIZED` both
      succeed; `capture` from any state other than `AUTHORIZED` is rejected; `authorize` from a
      non-`NONE` state is rejected (idempotency guard against a duplicate Timer fire), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PaymentEntityTest.java`
      (depends on T012)
- [X] T020 [P] Add `PlayerPaymentProfileEntityTest` (KeyValueEntityTestKit) covering `linkCustomer`
      and `setDefaultPaymentMethod`, and `hasPaymentMethod()` before/after both are set, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PlayerPaymentProfileEntityTest.java`
      (depends on T014)
- [X] T021 [P] Add a unit test for `PricingPolicy.validate()` asserting it rejects a
      `commitmentWindow` over the Stripe-safe cap and accepts one comfortably under it, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PricingPolicyTest.java`
      (depends on T002)
- [X] T022 [P] Add test cases to `FacilityEntityTest` (or create it if it doesn't yet exist) for
      `setPricingPolicy`/`setStripeConnectedAccount`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/customer/facility/FacilityEntityTest.java`
      (depends on T005)
- [X] T023 [P] Add a test case to `ResourceEntityTest` (or create it if it doesn't yet exist) for
      `setPricingPolicyOverride`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/resource/ResourceEntityTest.java`
      (depends on T008)
- [X] T024 [P] Add `PaymentGateTest` covering both methods independently: `isPlayerPayable` true/false
      based on `PlayerPaymentProfile.hasPaymentMethod()` (including the `Optional.empty()` identity
      case), and `isFacilityPayable` true/false based on `PricingPolicy` presence combined with
      connected-account charges-enabled status, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PaymentGateTest.java`
      (depends on T016)

**Checkpoint**: `mvn -o compile` and `mvn -o test` succeed for the whole module. No observable booking
behavior has changed yet — nothing from this phase is wired into `CourtBookingWorkflow`,
`BookingEndpoint`, or the `Fulfilled` event path.

---

## Phase 3: User Story 1 - A returning player is charged for a court with no extra steps (Priority: P1) 🎯 MVP

**Goal**: A player with a saved payment method books normally; a hold is created unattended at the
commitment cutoff and captured at the resolution point, with the facility's share transferred.

**Independent Test**: Seed a `PlayerPaymentProfile`, run a normal booking conversation, verify the hold
is created at the commitment cutoff and captured at the resolution point with no message sent to the
player at either point.

### Tests for User Story 1

- [ ] T025 [P] [US1] Add a test verifying that publishing `ReservationEvent.Fulfilled` causes
      `PaymentSchedulingAction` to invoke `ReservationEntity::recordPaymentId` and schedule a
      commitment-cutoff Timer at `max(bookingTime, slotStart − commitmentWindow)`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PaymentSchedulingActionTest.java`
- [ ] T026 [P] [US1] Add a test verifying `CommitmentCutoffTimedAction.attemptHold`'s happy path:
      given a `PlayerPaymentProfile` with a payment method and a resolvable `PricingPolicy`, calling
      it results in `PaymentEntity` moving to `AUTHORIZED` and a resolution-point Timer being
      scheduled, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/CommitmentCutoffTimedActionTest.java`
- [ ] T027 [P] [US1] Add a test verifying the resolution-point capture path: firing it against an
      `AUTHORIZED` `PaymentEntity` results in `CAPTURED` state and a `StripeService.createTransferFromCharge`
      call using the resolved `PricingPolicy`'s commission split, in the same test file as T026
      (depends on T026's test scaffolding)
- [ ] T028 [P] [US1] Add a test verifying `CourtBookingWorkflow.book()`'s player-side gate: when
      `PaymentGate.isPlayerPayable(...)` is true, `book()` proceeds to `reservationGateway.submit()`
      exactly as it does today (no observable difference), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/orchestration/CourtBookingWorkflowTest.java`
      (create if it doesn't yet exist)
- [ ] T029 [P] [US1] Add a test verifying that cancelling a reservation before its commitment cutoff
      never results in a `PaymentEntity` being created (SC-003), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PaymentSchedulingActionTest.java`
- [ ] T030 [P] [US1] Add a test verifying FR-013: change a facility's `PricingPolicy` after a
      reservation is `FULFILLED` but before its commitment cutoff fires, then confirm
      `CommitmentCutoffTimedAction.attemptHold`'s resulting hold amount/commission reflects the *new*
      policy, not the one in effect at booking time, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/CommitmentCutoffTimedActionTest.java`
      (same file as T026)

### Implementation for User Story 1

- [ ] T031 [US1] Create `PaymentSchedulingAction` (Consumer, `@Consume.FromEventSourcedEntity(ReservationEntity.class)`),
      reacting to `Fulfilled`: resolve the effective `PricingPolicy` (resource override, else facility
      default — used here only to read `commitmentWindow` for scheduling; the price/commission
      themselves are re-resolved fresh at fire time per FR-013/T032), compute the commitment cutoff,
      call `ReservationEntity::recordPaymentId`, and schedule a single Akka Timer via
      `TimerScheduler` targeting `CommitmentCutoffTimedAction::attemptHold` with `attemptNumber=1`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PaymentSchedulingAction.java`
      (depends on T012, T017)
- [ ] T032 [US1] Implement `CommitmentCutoffTimedAction.attemptHold(HoldAttempt command)` (happy path
      only — FR-016's retry/backoff branching is Phase 6/US4's job, but the method signature/skeleton
      must exist now): re-resolve the effective `PricingPolicy` fresh (FR-013 — this is what T030
      verifies), look up `PlayerPaymentProfile`, call `StripeService.createAndConfirmHold(...)`, and on
      success invoke `PaymentEntity::authorize` then schedule a resolution-point Timer targeting
      `CommitmentCutoffTimedAction::captureHold`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/CommitmentCutoffTimedAction.java`
      (depends on T012, T014, T015)
- [ ] T033 [US1] Implement `CommitmentCutoffTimedAction.captureHold(String reservationId)`: call
      `StripeService.capturePaymentIntent` then `createTransferFromCharge` using the resolved
      `PricingPolicy`'s commission split and the facility's `stripeConnectedAccountId`, then invoke
      `PaymentEntity::capture`, in the same file as T032 (depends on T032)
- [ ] T034 [US1] Add a sealed `BookingHandle` result type (`Booked(ReservationHandle)` |
      `CardSetupRequired(String checkoutUrl)` | `FacilityNotPayable`) and change
      `CourtBookingWorkflow.book()`'s return type to it (and `BookingWorkflow`'s interface signature to
      match), implementing the player-side FR-005 gate as the first check — call
      `PaymentGate.isPlayerPayable(origin.identityUserId())`; if true, proceed exactly as today (**US2
      implements the `CardSetupRequired` branch's link generation — this task wires the branching
      structure and US1's own passing case**) — and update `BookingTools.bookCourt()` to `switch` on
      the result instead of always returning `"BOOKING_SUBMITTED:" + id`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/CourtBookingWorkflow.java`,
      `.../BookingWorkflow.java`, and
      `reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingTools.java`
      (depends on T016)

**Checkpoint**: User Story 1 is independently demoable — a returning player's booking produces a
captured payment with a facility transfer, verifiable in Stripe test mode end-to-end (quickstart.md).

---

## Phase 4: User Story 2 - A first-time player puts a card on file once, before their first court is ever locked (Priority: P1)

**Goal**: A first-time player is never allowed to lock a court before a payment method exists;
completing the Stripe-hosted card-collection flow makes their next attempt behave like User Story 1.

**Independent Test**: Attempt to book with no `PlayerPaymentProfile`; verify no reservation is created
and a card-collection link is surfaced; complete it via webhook; verify a subsequent attempt succeeds
normally.

### Tests for User Story 2

- [ ] T035 [P] [US2] Add a test verifying `StripeWebhookEndpoint` handling `setup_intent.succeeded`
      populates `PlayerPaymentProfile.stripeCustomerId`/`defaultPaymentMethodId` for the `userId` in
      the event's metadata, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/StripeWebhookEndpointIntegrationTest.java`
- [ ] T036 [P] [US2] Add a test verifying webhook idempotency (FR-006, SC-006): deliver the same
      `payment_intent.succeeded` event twice (same Stripe event id/payload) against an already-`AUTHORIZED`
      `PaymentEntity` and confirm the second delivery is a no-op — `PaymentEntity` ends in a single
      correct state (`CAPTURED`, not corrupted or double-transitioned) and
      `StripeService.createTransferFromCharge` is invoked at most once, in the same test file as T035
- [ ] T037 [P] [US2] Add a test verifying that when `PaymentGate.isPlayerPayable(...)` is false,
      `CourtBookingWorkflow.book()` returns `CardSetupRequired` and **no** `ReservationEntity` is ever
      initialized for that attempt, in the `CourtBookingWorkflowTest.java` created in T028 (depends on
      T028's scaffolding)
- [ ] T038 [P] [US2] Add a test verifying that a player whose `PlayerPaymentProfile` already resolves
      (e.g. via a pre-seeded profile simulating the Hit-link flow) skips the card-collection branch
      entirely on their very first Rez booking, in the same test file as T037

### Implementation for User Story 2

- [ ] T039 [US2] Create `StripeWebhookEndpoint` (`POST /webhooks/stripe`), modeled on `hit-backend`'s
      (signature verification via `Webhook.constructEvent`, raw-JSON parsing, `200 OK` in no-op mode):
      routes `setup_intent.succeeded`/`payment_method.attached` to `PlayerPaymentProfileEntity::linkCustomer`/`::setDefaultPaymentMethod`,
      and `payment_intent.succeeded`/`payment_intent.payment_failed` to idempotent
      `PaymentEntity` reconciliation calls (a no-op if the entity is already in the target state — see
      data-model.md and T036), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/StripeWebhookEndpoint.java`
      (depends on T012, T014)
- [ ] T040 [US2] Add a `createCardSetupLink(String customerIdOrNull, String userId, String
      returnUrl)` method to `StripeService` (Checkout Session in setup mode, or a Payment Link,
      carrying `userId` in metadata so the webhook in T039 can resolve it back), and wire
      `CourtBookingWorkflow`'s `CardSetupRequired` branch (from T034) to call it and surface the
      resulting URL through `BookingTools.bookCourt()`'s reply text, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/infrastructure/StripeService.java`
      and `.../orchestration/CourtBookingWorkflow.java` (depends on T015, T034)

**Checkpoint**: User Stories 1 and 2 together form a complete first-payment lifecycle — a brand-new
player can go from no card on file to a captured payment across two booking attempts.

---

## Phase 5: User Story 3 - A facility configures what a slot costs and how much runway players get to cancel for free (Priority: P2)

**Goal**: An admin can set `PricingPolicy` (and a per-resource override) and Stripe connected-account
id via direct API calls; an unsafe `commitmentWindow` is rejected; a facility without completed
onboarding cannot produce a payable booking through **either** booking-creation entry point.

**Independent Test**: Configure `PricingPolicy` on a facility (and an override on one resource), book
that resource, confirm the override applies. Attempt an over-cap `commitmentWindow`, confirm rejection.
Attempt a booking at a facility with `PricingPolicy` but no completed onboarding via both
`CourtBookingWorkflow` (agent path) and `BookingEndpoint` (direct HTTP path), confirm both reject it.

### Tests for User Story 3

- [ ] T041 [P] [US3] Add `FacilityEndpointIntegrationTest` cases for `PUT
      /facility/{facilityId}/pricing-policy` and `PUT /facility/{facilityId}/stripe-connected-account`,
      including a `commitmentWindow`-over-cap request returning `400`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/FacilityEndpointIntegrationTest.java`
      (create if it doesn't yet exist)
- [ ] T042 [P] [US3] Add `ResourceEndpointIntegrationTest` cases for `PUT
      /resource/{resourceId}/pricing-policy`, plus a booking-level test confirming a resource-level
      override takes precedence over its facility's default when resolving the effective policy, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/ResourceEndpointIntegrationTest.java`
      (create if it doesn't yet exist)
- [ ] T043 [P] [US3] Add a test verifying `CourtBookingWorkflow.book()` returns `FacilityNotPayable`
      and submits no reservation when the target facility has a `PricingPolicy` but no completed
      Stripe connected-account onboarding (FR-012, SC-007), in the `CourtBookingWorkflowTest.java`
      from T028
- [ ] T044 [P] [US3] Add a test verifying `BookingEndpoint.book()` rejects (returns `400`, submits no
      `ReservationEntity::init` call) when the target facility (resolved from `resourceIds`) has a
      `PricingPolicy` but no completed onboarding — the FR-012 half of what T043 verifies on the agent
      path, exercised here on the direct HTTP path per research.md #10, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/BookingEndpointIntegrationTest.java`
      (create if it doesn't yet exist)

### Implementation for User Story 3

- [ ] T045 [US3] Add `PUT /pricing-policy` and `PUT /stripe-connected-account` methods to
      `FacilityEndpoint`, invoking the T005 command handlers and returning `400` when
      `PricingPolicy.validate()` throws, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/FacilityEndpoint.java`
      (depends on T005)
- [ ] T046 [US3] Add a `PUT /pricing-policy` method to `ResourceEndpoint`, invoking the T008 command
      handler, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/ResourceEndpoint.java`
      (depends on T008)
- [ ] T047 [P] [US3] Extend the `Facility` and `Resource` API response DTOs with the new fields
      (`pricingPolicy`, `stripeConnectedAccountId` on `Facility`; `pricingPolicyOverride` on
      `Resource`), additive only, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/dto/Facility.java`
      and `reservation/reservation/src/main/java/com/rezhub/reservation/resource/dto/Resource.java`
      (depends on T005, T008)
- [ ] T048 [US3] Implement the `FacilityNotPayable` branch in `CourtBookingWorkflow.book()`: call
      `PaymentGate.isFacilityPayable(facilityId)` and reject when false (FR-012), extending the
      `BookingHandle` switch from T034, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/CourtBookingWorkflow.java`
      (depends on T034, T016)
- [ ] T049 [US3] Wire `BookingEndpoint.book()` to resolve the target facility from the request's
      `resourceIds` (via `ResourcesByFacilityView`, same lookup `CourtDirectoryAkka` already uses) and
      call `PaymentGate.isFacilityPayable(facilityId)` before `ReservationEntity::init`, returning a
      `400 Bad Request` when false instead of submitting the reservation. Does **not** call
      `isPlayerPayable` — `BookingRequest` has no player-identity field to check (research.md #10;
      spec.md Out of Scope), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/BookingEndpoint.java` (depends
      on T016)

**Checkpoint**: Facilities can be fully configured and validated; an unconfigured/unpayable facility
can no longer produce a court lock that's doomed to fail payment, on **either** booking-creation entry
point.

---

## Phase 6: User Story 4 - A payment failure at the commitment cutoff never leaves a court held for a slot that won't get paid for (Priority: P2)

**Goal**: A transient Stripe/network failure retries automatically with no player involvement; a
genuine card failure notifies the player with a bounded grace window; either way, an unresolved failure
converges on cancelling the reservation and releasing the court.

**Independent Test**: Simulate a transient error and confirm silent automatic retry; simulate a genuine
decline/`authentication_required` and confirm notification + grace window, converging on cancellation
if unresolved.

### Tests for User Story 4

- [ ] T050 [P] [US4] Add a test verifying `CommitmentCutoffTimedAction.attemptHold` reschedules itself
      with backoff and an incremented `attemptNumber` on a transient `StripeService` failure, sending
      no player notification, in `CommitmentCutoffTimedActionTest.java` (from T026)
- [ ] T051 [P] [US4] Add a test verifying that a genuine card-specific failure (or exhausted retries)
      triggers a player notification (re-authentication/new-card link) and schedules a grace-window
      Timer, in the same test file as T050
- [ ] T052 [P] [US4] Add a test verifying that when the grace-window Timer fires with no successful
      hold in the interim, `PaymentEntity::fail` and `ReservationEntity::cancelRequest` are both
      invoked, releasing the court, in the same test file as T050

### Implementation for User Story 4

- [ ] T053 [US4] Implement FR-016 in `CommitmentCutoffTimedAction.attemptHold`: classify
      `StripeService` exceptions as transient (network/API-availability) vs. card-specific
      (`authentication_required`, decline, expired card); on transient, reschedule via
      `TimerScheduler` with backoff and `attemptNumber + 1` up to a configured max; on card-specific,
      or once the max is reached, proceed to T054, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/CommitmentCutoffTimedAction.java`
      (depends on T032)
- [ ] T054 [US4] Implement the FR-010 notification path: on the failure branch from T053, notify the
      player via the existing `NotificationSender`/`telegramnotifier` SPI with a re-authentication/
      new-card link **when a resolved identity/notification channel exists** (a `BookingEndpoint`-
      originated reservation has none — see spec.md's Edge Cases; the notify step is then a no-op, not
      a different outcome), invoke `PaymentEntity::fail` only if no hold was ever authorized (leave
      `AUTHORIZED` holds alone here — T053 only reaches this path pre-`AUTHORIZED`), and schedule a
      grace-window Timer targeting a new `onGraceWindowExpired` method, in the same file as T053
      (depends on T053)
- [ ] T055 [US4] Implement `onGraceWindowExpired(String reservationId)`: check whether a hold
      succeeded in the interim (re-query `PaymentEntity` state); if still not `AUTHORIZED`, invoke
      `PaymentEntity::fail` (if not already `FAILED`) and `ReservationEntity::cancelRequest`, in the
      same file as T053/T054 (depends on T054)

**Checkpoint**: All four user stories are independently verified. Every payment-failure path converges
on a definite outcome (SC-005), and transient errors never reach the player unnecessarily (SC-008).

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T056 [P] Run `mvn -o verify` from `reservation/reservation/` to confirm all new and existing
      tests pass together
- [ ] T057 Execute `quickstart.md`'s manual verification steps end-to-end against a locally running
      stack with Stripe test mode, per `specs/002-payment-core/quickstart.md`
- [ ] T058 Update `spec.md`'s Status field from `Draft` to a closed/complete status once T056 and T057
      both pass, in `specs/002-payment-core/spec.md`
- [ ] T059 [P] If implementation surfaced any refinement to the converged mechanism worth recording
      (e.g. an off-session-confirmation detail research.md #7 didn't fully anticipate), reconcile
      `docs/payments-cancellation-waitlist-design.md`'s Phase 1 section accordingly — only if something
      genuinely diverged; do not edit speculatively, in
      `/Users/max/code/rez/docs/payments-cancellation-waitlist-design.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories (no story compiles against
  the new domain types/entities until this phase is done)
- **User Story 1 (Phase 3)**: Depends on Phase 2. This is the MVP.
- **User Story 2 (Phase 4)**: Depends on Phase 3 — shares `CourtBookingWorkflow.book()`'s gate
  structure built in T034; the `CardSetupRequired` branch's actual link generation is this phase's job
- **User Story 3 (Phase 5)**: Depends on Phase 2 for the domain plumbing (T003–T008, T016); depends on
  Phase 3's T034 (`BookingHandle`) for its `FacilityNotPayable` branch (T048) — otherwise independent
  of Phase 4. T049 (`BookingEndpoint` gating) depends only on Phase 2's T016, not on Phase 3 at all.
- **User Story 4 (Phase 6)**: Depends on Phase 3's T032 (`attemptHold`'s happy-path skeleton must
  exist before its failure branches can be added) — independent of Phases 4/5
- **Polish (Final Phase)**: Depends on Phases 3–6 all being complete

### Within Each Phase

- Tests before implementation in every user-story phase (write first, confirm they fail, then implement)
- Domain/entity fields before the components that read/write them (e.g. T002 before T003/T006; T011
  before T012; T005/T014/T015 before T016)
- Within Phase 6: T053 before T054 before T055 (each builds directly on the previous method's
  branching)

### Parallel Opportunities

- T002, T009, T011, T013, T015 (five independent domain/infrastructure files, no dependency between
  them)
- T003 and T006 (different entities, both depend only on T002)
- T018, T019, T020, T021, T022, T023, T024 (seven independent test files/additions)
- T025 and T028 (different test files, both depend only on Phase 2)
- T041, T042, T043, T044 (four independent test files)
- T048 and T049 (different files — `CourtBookingWorkflow` vs `BookingEndpoint` — both depend only on
  T016, not on each other)
- T050, T051, T052 (same file, but written as independent test methods before their shared
  implementation exists — list together for planning purposes even though not literally different
  files)

---

## Parallel Example: Foundational Phase

```
Task: "Create PricingPolicy domain record in reservation/reservation/src/main/java/com/rezhub/reservation/payment/PricingPolicy.java"
Task: "Add paymentId field to ReservationState in reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationState.java"
Task: "Create PaymentState + PaymentEvent in reservation/reservation/src/main/java/com/rezhub/reservation/payment/"
Task: "Create PlayerPaymentProfileState in reservation/reservation/src/main/java/com/rezhub/reservation/payment/PlayerPaymentProfileState.java"
Task: "Create StripeService in reservation/reservation/src/main/java/com/rezhub/reservation/infrastructure/StripeService.java"
```

## Parallel Example: User Story 3

```
Task: "FacilityEndpointIntegrationTest cases for pricing-policy/stripe-connected-account endpoints"
Task: "ResourceEndpointIntegrationTest cases for pricing-policy override + precedence"
Task: "CourtBookingWorkflow test for FacilityNotPayable"
Task: "BookingEndpoint test for facility-payable rejection"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational) — required, nothing compiles or delivers value
   without them
2. Complete Phase 3 (User Story 1)
3. **STOP and VALIDATE**: run T025–T030, confirm all pass; walk quickstart.md's User Story 1 section
   against a local Stripe-test-mode stack
4. This is a deployable MVP — Rez can charge a returning player for a booking

### Incremental Delivery

1. Setup + Foundational → compiles, zero behavior change
2. Phase 3 (US1) → returning-player payment lifecycle live → MVP
3. Phase 4 (US2) → first-time card collection live → every player can now reach "returning player"
4. Phase 5 (US3) → facility admin configuration + onboarding gate live on **both** entry points
5. Phase 6 (US4) → failure paths hardened
6. Polish → full `mvn verify`, quickstart run, spec closed out

### Note on parallel team strategy

Phases 3 and 4 are tightly coupled (shared `BookingHandle`/gate structure from T034) and should stay
with one implementer through both. Phases 5 and 6, once Phase 3 lands, are genuinely independent of
each other and of Phase 4 — a reasonable split for two people is "Phase 3 → Phase 4" for one
implementer and "Phase 5" + "Phase 6" (after Phase 3's T032/T034 land) for a second. Within Phase 5,
T048 (`CourtBookingWorkflow`) and T049 (`BookingEndpoint`) touch different files and can themselves be
split further if needed.

---

## Notes

- [P] tasks touch different files with no unfinished dependency between them
- [Story] labels trace each task back to spec.md's user stories
- Commit after each phase (Setup, Foundational, US1, US2, US3, US4, Polish) — matches this project's
  established discipline
- `mvn compile` before every commit
- The `reservation/reservation/pom.xml` version bump (T001) is committed separately from any code
  change, per this project's Maven-versioning convention
