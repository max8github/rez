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

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Maps the task to a user story from spec.md (US1, US2, US3, US4)
- File paths are relative to `/Users/max/code/rez/` unless given as absolute paths

---

## Phase 1: Setup

- [ ] T001 Add `com.stripe:stripe-java:27.1.0` dependency to
      `reservation/reservation/pom.xml`; bump the `reservation` module's own version per this
      project's Maven-versioning convention (fixed versions only, pom change committed separately
      from code — see `feedback_maven_versioning`); confirm `mvn -o compile` still succeeds from
      `reservation/reservation/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The new domain types, entities, and infrastructure client every user story depends on.
Nothing here is itself user-visible — no component is wired into the booking flow yet. Must compile
and pass its own unit tests before any story-specific work begins.

- [ ] T002 [P] Create `PricingPolicy` domain record (`priceCents`, `currency`, `commissionFraction`,
      `commitmentWindow: Duration`) with a `validate()` method enforcing FR-011's Stripe-safe cap, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PricingPolicy.java`
- [ ] T003 [P] Add `Optional<PricingPolicy> pricingPolicy` and `Optional<String>
      stripeConnectedAccountId` fields to `FacilityState`, with `withPricingPolicy(...)` and
      `withStripeConnectedAccountId(...)` builder methods following the existing `with*` pattern, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityState.java`
      (depends on T002)
- [ ] T004 Add `PricingPolicySet(String facilityId, PricingPolicy policy)` and
      `StripeConnectedAccountSet(String facilityId, String accountId)` variants to `FacilityEvent`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityEvent.java`
      (depends on T002)
- [ ] T005 Add `setPricingPolicy(PricingPolicy)` and `setStripeConnectedAccount(String)` command
      handlers to `FacilityEntity`, persisting the two new events and updating `applyEvent`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityEntity.java`
      (depends on T003, T004)
- [ ] T006 [P] Add `Optional<PricingPolicy> pricingPolicyOverride` field to `ResourceState`, with a
      `withPricingPolicyOverride(...)` builder method following the existing `with*` pattern, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceState.java`
      (depends on T002)
- [ ] T007 Add `PricingPolicyOverrideSet(String resourceId, PricingPolicy policy)` variant to
      `ResourceEvent`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEvent.java`
      (depends on T002)
- [ ] T008 Add `setPricingPolicyOverride(PricingPolicy)` command handler to `ResourceEntity`,
      persisting the new event and updating `applyEvent`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java`
      (depends on T006, T007)
- [ ] T009 [P] Add `Optional<String> paymentId` field to `ReservationState`, defaulting to
      `Optional.empty()` in `initiate()`, with a `withPaymentId(Optional<String>)` builder method and
      the field appended to every existing `with*` method's positional reconstruction, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationState.java`
- [ ] T010 Add a `PaymentIdRecorded(String paymentId)` event and a corresponding
      `recordPaymentId(String paymentId)` command handler to `ReservationEntity` (valid only from
      `FULFILLED`), applying it via the new `withPaymentId` builder — see plan.md's "deliberate design
      note" for why this is a separate command rather than a field on `Fulfilled` itself, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java`
      and `.../ReservationEvent.java` (depends on T009)
- [ ] T011 [P] Create `PaymentState` (record + `State` enum: `NONE, AUTHORIZED, CAPTURED, VOIDED,
      REFUNDED, FAILED`) and `PaymentEvent` (sealed interface: `HoldAuthorized`, `HoldCaptured`,
      `HoldCreationFailed`, each `@TypeName`-annotated) per data-model.md, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PaymentState.java` and
      `.../PaymentEvent.java`
- [ ] T012 Create `PaymentEntity` (Event Sourced) with `authorize(Authorize)`, `capture(Capture)`, and
      `fail(Fail)` command handlers per data-model.md's state-transition rules (each rejects from an
      invalid source state — see data-model.md's idempotency-guard note); **do not** add `void()` or
      `refund()` handlers (FR-017), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PaymentEntity.java`
      (depends on T011)
- [ ] T013 [P] Create `PlayerPaymentProfileState` record (`userId`, `Optional<String>
      stripeCustomerId`, `Optional<String> defaultPaymentMethodId`, `hasPaymentMethod()`), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PlayerPaymentProfileState.java`
- [ ] T014 Create `PlayerPaymentProfileEntity` (Key Value Entity) with `linkCustomer(String)` and
      `setDefaultPaymentMethod(String)` command handlers, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PlayerPaymentProfileEntity.java`
      (depends on T013)
- [ ] T015 [P] Create `StripeService` in
      `reservation/reservation/src/main/java/com/rezhub/reservation/infrastructure/StripeService.java`,
      modeled on `hit-backend`'s (research.md #7): no-op mode when `STRIPE_SECRET_KEY` is absent,
      idempotency keys on every mutating call. Methods needed by this feature: off-session
      `createAndConfirmHold(long amountCents, String currency, String customerId, String
      paymentMethodId, String idempotencyKey)` (unlike Hit's client-confirmed
      `createPaymentIntent`, this one sets `.setCustomer().setPaymentMethod().setOffSession(true).setConfirm(true)`
      in one call — see research.md #7), `capturePaymentIntent`, `createTransferFromCharge`,
      `createCustomer`, `createConnectAccount`, `isConnectAccountChargesEnabled` (depends on T001)
- [ ] T016 Create `SlotPaymentView`, keyed by `resourceId + dateTime`, sourced from
      `PaymentEvent.HoldAuthorized`/`HoldCaptured`/`HoldCreationFailed` and
      `ReservationEvent.Fulfilled` (for the `resourceId + dateTime` key itself — see data-model.md),
      in `reservation/reservation/src/main/java/com/rezhub/reservation/payment/SlotPaymentView.java`
      (depends on T012)
- [ ] T017 [P] Add `PaymentEntityTest` (EventSourcedTestKit) covering: `authorize` from `NONE`
      succeeds; `capture` from `AUTHORIZED` succeeds; `fail` from `NONE` and from `AUTHORIZED` both
      succeed; `capture` from any state other than `AUTHORIZED` is rejected; `authorize` from a
      non-`NONE` state is rejected (idempotency guard against a duplicate Timer fire), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PaymentEntityTest.java`
      (depends on T012)
- [ ] T018 [P] Add `PlayerPaymentProfileEntityTest` (KeyValueEntityTestKit) covering `linkCustomer`
      and `setDefaultPaymentMethod`, and `hasPaymentMethod()` before/after both are set, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PlayerPaymentProfileEntityTest.java`
      (depends on T014)
- [ ] T019 [P] Add a unit test for `PricingPolicy.validate()` asserting it rejects a
      `commitmentWindow` over the Stripe-safe cap and accepts one comfortably under it, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PricingPolicyTest.java`
      (depends on T002)
- [ ] T020 [P] Add test cases to `FacilityEntityTest` (or create it if it doesn't yet exist) for
      `setPricingPolicy`/`setStripeConnectedAccount`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/customer/facility/FacilityEntityTest.java`
      (depends on T005)
- [ ] T021 [P] Add a test case to `ResourceEntityTest` (or create it if it doesn't yet exist) for
      `setPricingPolicyOverride`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/resource/ResourceEntityTest.java`
      (depends on T008)

**Checkpoint**: `mvn -o compile` and `mvn -o test` succeed for the whole module. No observable booking
behavior has changed yet — nothing from this phase is wired into `CourtBookingWorkflow` or the
`Fulfilled` event path.

---

## Phase 3: User Story 1 - A returning player is charged for a court with no extra steps (Priority: P1) 🎯 MVP

**Goal**: A player with a saved payment method books normally; a hold is created unattended at the
commitment cutoff and captured at the resolution point, with the facility's share transferred.

**Independent Test**: Seed a `PlayerPaymentProfile`, run a normal booking conversation, verify the hold
is created at the commitment cutoff and captured at the resolution point with no message sent to the
player at either point.

### Tests for User Story 1

- [ ] T022 [P] [US1] Add a test verifying that publishing `ReservationEvent.Fulfilled` causes
      `PaymentSchedulingAction` to invoke `ReservationEntity::recordPaymentId` and schedule a
      commitment-cutoff Timer at `max(bookingTime, slotStart − commitmentWindow)`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PaymentSchedulingActionTest.java`
- [ ] T023 [P] [US1] Add a test verifying `CommitmentCutoffTimedAction.attemptHold`'s happy path:
      given a `PlayerPaymentProfile` with a payment method and a resolvable `PricingPolicy`, calling
      it results in `PaymentEntity` moving to `AUTHORIZED` and a resolution-point Timer being
      scheduled, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/CommitmentCutoffTimedActionTest.java`
- [ ] T024 [P] [US1] Add a test verifying the resolution-point capture path: firing it against an
      `AUTHORIZED` `PaymentEntity` results in `CAPTURED` state and a `StripeService.createTransferFromCharge`
      call using the resolved `PricingPolicy`'s commission split, in the same test file as T023
      (depends on T023's test scaffolding)
- [ ] T025 [P] [US1] Add a test verifying `CourtBookingWorkflow.book()`'s player-side gate: when
      `PlayerPaymentProfile.hasPaymentMethod()` is true, `book()` proceeds to
      `reservationGateway.submit()` exactly as it does today (no observable difference), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/orchestration/CourtBookingWorkflowTest.java`
      (create if it doesn't yet exist)
- [ ] T026 [P] [US1] Add a test verifying that cancelling a reservation before its commitment cutoff
      never results in a `PaymentEntity` being created (SC-003), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/payment/PaymentSchedulingActionTest.java`

### Implementation for User Story 1

- [ ] T027 [US1] Create `PaymentSchedulingAction` (Consumer, `@Consume.FromEventSourcedEntity(ReservationEntity.class)`),
      reacting to `Fulfilled`: resolve the effective `PricingPolicy` (resource override, else facility
      default — used here only to read `commitmentWindow` for scheduling; the price/commission
      themselves are re-resolved fresh at fire time per FR-013/T028), compute the commitment cutoff,
      call `ReservationEntity::recordPaymentId`, and schedule a single Akka Timer via
      `TimerScheduler` targeting `CommitmentCutoffTimedAction::attemptHold` with `attemptNumber=1`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/PaymentSchedulingAction.java`
      (depends on T012, T016)
- [ ] T028 [US1] Implement `CommitmentCutoffTimedAction.attemptHold(HoldAttempt command)` (happy path
      only — FR-016's retry/backoff branching is Phase 6/US4's job, but the method signature/skeleton
      must exist now): re-resolve the effective `PricingPolicy` fresh (FR-013), look up
      `PlayerPaymentProfile`, call `StripeService.createAndConfirmHold(...)`, and on success invoke
      `PaymentEntity::authorize` then schedule a resolution-point Timer targeting
      `CommitmentCutoffTimedAction::captureHold`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/CommitmentCutoffTimedAction.java`
      (depends on T012, T014, T015)
- [ ] T029 [US1] Implement `CommitmentCutoffTimedAction.captureHold(String reservationId)`: call
      `StripeService.capturePaymentIntent` then `createTransferFromCharge` using the resolved
      `PricingPolicy`'s commission split and the facility's `stripeConnectedAccountId`, then invoke
      `PaymentEntity::capture`, in the same file as T028 (depends on T028)
- [ ] T030 [US1] Add a sealed `BookingHandle` result type (`Booked(ReservationHandle)` |
      `CardSetupRequired(String checkoutUrl)` | `FacilityNotPayable`) and change
      `CourtBookingWorkflow.book()`'s return type to it, implementing the player-side FR-005 gate as
      the first check (query `PlayerPaymentProfile` for `origin`'s resolved `userId`; if
      `hasPaymentMethod()`, proceed exactly as today; **US2 implements the `CardSetupRequired` branch's
      link generation — this task wires the branching structure and US1's own passing case**), and
      update `BookingTools.bookCourt()` to `switch` on the result instead of always returning
      `"BOOKING_SUBMITTED:" + id`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/CourtBookingWorkflow.java`,
      `.../BookingWorkflow.java`, and
      `reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingTools.java`
      (depends on T014)

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

- [ ] T031 [P] [US2] Add a test verifying `StripeWebhookEndpoint` handling `setup_intent.succeeded`
      populates `PlayerPaymentProfile.stripeCustomerId`/`defaultPaymentMethodId` for the `userId` in
      the event's metadata, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/StripeWebhookEndpointIntegrationTest.java`
- [ ] T032 [P] [US2] Add a test verifying that when `PlayerPaymentProfile.hasPaymentMethod()` is
      false, `CourtBookingWorkflow.book()` returns `CardSetupRequired` and **no** `ReservationEntity`
      is ever initialized for that attempt, in the `CourtBookingWorkflowTest.java` created in T025
      (depends on T025's scaffolding)
- [ ] T033 [P] [US2] Add a test verifying that a player whose `PlayerPaymentProfile` already resolves
      (e.g. via a pre-seeded profile simulating the Hit-link flow) skips the card-collection branch
      entirely on their very first Rez booking, in the same test file as T032

### Implementation for User Story 2

- [ ] T034 [US2] Create `StripeWebhookEndpoint` (`POST /webhooks/stripe`), modeled on `hit-backend`'s
      (signature verification via `Webhook.constructEvent`, raw-JSON parsing, `200 OK` in no-op mode):
      routes `setup_intent.succeeded`/`payment_method.attached` to `PlayerPaymentProfileEntity::linkCustomer`/`::setDefaultPaymentMethod`,
      and `payment_intent.succeeded`/`payment_intent.payment_failed` to idempotent
      `PaymentEntity` reconciliation calls (a no-op if the entity is already in the target state — see
      data-model.md), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/StripeWebhookEndpoint.java`
      (depends on T012, T014)
- [ ] T035 [US2] Add a `createCardSetupLink(String customerIdOrNull, String userId, String
      returnUrl)` method to `StripeService` (Checkout Session in setup mode, or a Payment Link,
      carrying `userId` in metadata so the webhook in T034 can resolve it back), and wire
      `CourtBookingWorkflow`'s `CardSetupRequired` branch (from T030) to call it and surface the
      resulting URL through `BookingTools.bookCourt()`'s reply text, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/infrastructure/StripeService.java`
      and `.../orchestration/CourtBookingWorkflow.java` (depends on T015, T030)

**Checkpoint**: User Stories 1 and 2 together form a complete first-payment lifecycle — a brand-new
player can go from no card on file to a captured payment across two booking attempts.

---

## Phase 5: User Story 3 - A facility configures what a slot costs and how much runway players get to cancel for free (Priority: P2)

**Goal**: An admin can set `PricingPolicy` (and a per-resource override) and Stripe connected-account
id via direct API calls; an unsafe `commitmentWindow` is rejected; a facility without completed
onboarding cannot produce a payable booking.

**Independent Test**: Configure `PricingPolicy` on a facility (and an override on one resource), book
that resource, confirm the override applies. Attempt an over-cap `commitmentWindow`, confirm rejection.
Attempt a booking at a facility with `PricingPolicy` but no completed onboarding, confirm rejection.

### Tests for User Story 3

- [ ] T036 [P] [US3] Add `FacilityEndpointIntegrationTest` cases for `PUT
      /facility/{facilityId}/pricing-policy` and `PUT /facility/{facilityId}/stripe-connected-account`,
      including a `commitmentWindow`-over-cap request returning `400`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/FacilityEndpointIntegrationTest.java`
      (create if it doesn't yet exist)
- [ ] T037 [P] [US3] Add `ResourceEndpointIntegrationTest` cases for `PUT
      /resource/{resourceId}/pricing-policy`, plus a booking-level test confirming a resource-level
      override takes precedence over its facility's default when resolving the effective policy, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/ResourceEndpointIntegrationTest.java`
      (create if it doesn't yet exist)
- [ ] T038 [P] [US3] Add a test verifying `CourtBookingWorkflow.book()` returns `FacilityNotPayable`
      and submits no reservation when the target facility has a `PricingPolicy` but no completed
      Stripe connected-account onboarding (FR-012, SC-007), in the `CourtBookingWorkflowTest.java`
      from T025

### Implementation for User Story 3

- [ ] T039 [US3] Add `PUT /pricing-policy` and `PUT /stripe-connected-account` methods to
      `FacilityEndpoint`, invoking the T005 command handlers and returning `400` when
      `PricingPolicy.validate()` throws, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/FacilityEndpoint.java`
      (depends on T005)
- [ ] T040 [US3] Add a `PUT /pricing-policy` method to `ResourceEndpoint`, invoking the T008 command
      handler, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/ResourceEndpoint.java`
      (depends on T008)
- [ ] T041 [P] [US3] Extend the `Facility` and `Resource` API response DTOs with the new fields
      (`pricingPolicy`, `stripeConnectedAccountId` on `Facility`; `pricingPolicyOverride` on
      `Resource`), additive only, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/customer/facility/dto/Facility.java`
      and `reservation/reservation/src/main/java/com/rezhub/reservation/resource/dto/Resource.java`
      (depends on T005, T008)
- [ ] T042 [US3] Implement the `FacilityNotPayable` branch in `CourtBookingWorkflow.book()`: reject
      when the target facility has a `PricingPolicy` set but `stripeConnectedAccountId` is absent or
      `StripeService.isConnectAccountChargesEnabled(...)` returns false (FR-012), extending the
      `BookingHandle` switch from T030, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/CourtBookingWorkflow.java`
      (depends on T030, T015)

**Checkpoint**: Facilities can be fully configured and validated; an unconfigured/unpayable facility
can no longer produce a court lock that's doomed to fail payment.

---

## Phase 6: User Story 4 - A payment failure at the commitment cutoff never leaves a court held for a slot that won't get paid for (Priority: P2)

**Goal**: A transient Stripe/network failure retries automatically with no player involvement; a
genuine card failure notifies the player with a bounded grace window; either way, an unresolved failure
converges on cancelling the reservation and releasing the court.

**Independent Test**: Simulate a transient error and confirm silent automatic retry; simulate a genuine
decline/`authentication_required` and confirm notification + grace window, converging on cancellation
if unresolved.

### Tests for User Story 4

- [ ] T043 [P] [US4] Add a test verifying `CommitmentCutoffTimedAction.attemptHold` reschedules itself
      with backoff and an incremented `attemptNumber` on a transient `StripeService` failure, sending
      no player notification, in `CommitmentCutoffTimedActionTest.java` (from T023)
- [ ] T044 [P] [US4] Add a test verifying that a genuine card-specific failure (or exhausted retries)
      triggers a player notification (re-authentication/new-card link) and schedules a grace-window
      Timer, in the same test file as T043
- [ ] T045 [P] [US4] Add a test verifying that when the grace-window Timer fires with no successful
      hold in the interim, `PaymentEntity::fail` and `ReservationEntity::cancelRequest` are both
      invoked, releasing the court, in the same test file as T043

### Implementation for User Story 4

- [ ] T046 [US4] Implement FR-016 in `CommitmentCutoffTimedAction.attemptHold`: classify
      `StripeService` exceptions as transient (network/API-availability) vs. card-specific
      (`authentication_required`, decline, expired card); on transient, reschedule via
      `TimerScheduler` with backoff and `attemptNumber + 1` up to a configured max; on card-specific,
      or once the max is reached, proceed to T047, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/payment/CommitmentCutoffTimedAction.java`
      (depends on T028)
- [ ] T047 [US4] Implement the FR-010 notification path: on the failure branch from T046, notify the
      player via the existing `NotificationSender`/`telegramnotifier` SPI with a re-authentication/
      new-card link, invoke `PaymentEntity::fail` only if no hold was ever authorized (leave
      `AUTHORIZED` holds alone here — T046 only reaches this path pre-`AUTHORIZED`), and schedule a
      grace-window Timer targeting a new `onGraceWindowExpired` method, in the same file as T046
      (depends on T046)
- [ ] T048 [US4] Implement `onGraceWindowExpired(String reservationId)`: check whether a hold
      succeeded in the interim (re-query `PaymentEntity` state); if still not `AUTHORIZED`, invoke
      `PaymentEntity::fail` (if not already `FAILED`) and `ReservationEntity::cancelRequest`, in the
      same file as T046/T047 (depends on T047)

**Checkpoint**: All four user stories are independently verified. Every payment-failure path converges
on a definite outcome (SC-005), and transient errors never reach the player unnecessarily (SC-008).

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T049 [P] Run `mvn -o verify` from `reservation/reservation/` to confirm all new and existing
      tests pass together
- [ ] T050 Execute `quickstart.md`'s manual verification steps end-to-end against a locally running
      stack with Stripe test mode, per `specs/002-payment-core/quickstart.md`
- [ ] T051 Update `spec.md`'s Status field from `Draft` to a closed/complete status once T049 and T050
      both pass, in `specs/002-payment-core/spec.md`
- [ ] T052 [P] If implementation surfaced any refinement to the converged mechanism worth recording
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
  structure built in T030; the `CardSetupRequired` branch's actual link generation is this phase's job
- **User Story 3 (Phase 5)**: Depends on Phase 2 for the domain plumbing (T003–T008); depends on Phase
  3's T030 (`BookingHandle`) for its `FacilityNotPayable` branch (T042) — otherwise independent of
  Phase 4
- **User Story 4 (Phase 6)**: Depends on Phase 3's T028 (`attemptHold`'s happy-path skeleton must
  exist before its failure branches can be added) — independent of Phases 4/5
- **Polish (Final Phase)**: Depends on Phases 3–6 all being complete

### Within Each Phase

- Tests before implementation in every user-story phase (write first, confirm they fail, then implement)
- Domain/entity fields before the components that read/write them (e.g. T002 before T003/T006; T011
  before T012)
- Within Phase 6: T046 before T047 before T048 (each builds directly on the previous method's
  branching)

### Parallel Opportunities

- T002, T009, T011, T013, T015 (five independent domain/infrastructure files, no dependency between
  them)
- T003 and T006 (different entities, both depend only on T002)
- T017, T018, T019, T020, T021 (five independent test files)
- T022 and T025 (different test files, both depend only on Phase 2)
- T036, T037, T038 (three independent test files)
- T043, T044, T045 (same file, but written as independent test methods before their shared
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
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational) — required, nothing compiles or delivers value
   without them
2. Complete Phase 3 (User Story 1)
3. **STOP and VALIDATE**: run T022–T026, confirm all pass; walk quickstart.md's User Story 1 section
   against a local Stripe-test-mode stack
4. This is a deployable MVP — Rez can charge a returning player for a booking

### Incremental Delivery

1. Setup + Foundational → compiles, zero behavior change
2. Phase 3 (US1) → returning-player payment lifecycle live → MVP
3. Phase 4 (US2) → first-time card collection live → every player can now reach "returning player"
4. Phase 5 (US3) → facility admin configuration + onboarding gate live
5. Phase 6 (US4) → failure paths hardened
6. Polish → full `mvn verify`, quickstart run, spec closed out

### Note on parallel team strategy

Phases 3 and 4 are tightly coupled (shared `BookingHandle`/gate structure from T030) and should stay
with one implementer through both. Phases 5 and 6, once Phase 3 lands, are genuinely independent of
each other and of Phase 4 — a reasonable split for two people is "Phase 3 → Phase 4" for one
implementer and "Phase 5" + "Phase 6" (after Phase 3's T028/T030 land) for a second.

---

## Notes

- [P] tasks touch different files with no unfinished dependency between them
- [Story] labels trace each task back to spec.md's user stories
- Commit after each phase (Setup, Foundational, US1, US2, US3, US4, Polish) — matches this project's
  established discipline
- `mvn compile` before every commit
- The `reservation/reservation/pom.xml` version bump (T001) is committed separately from any code
  change, per this project's Maven-versioning convention
