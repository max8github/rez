# Tasks: Telegram Identity Resolution

**Input**: Design documents from `specs/001-telegram-identity-resolution/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md (all present; no `contracts/` — no new external interface)

**Tests**: Included. Not optional for this project — the constitution's Test Coverage principle
("every behavioral change MUST be accompanied by tests") makes this a hard requirement, not a
judgment call.

**Organization**: Tasks are grouped by user story. Note up front: US1 and US3 share the same
underlying field-threading code by design (see plan.md's Summary and spec.md's US3 "Why this
priority") — US1 cannot satisfy its own acceptance criteria without the persistence US3 describes.
Phase 5 (US3) is therefore verification-focused rather than new production code; this is intentional,
not a gap.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to a user story from spec.md (US1, US2, US3)
- File paths are relative to `/Users/max/code/rez/` unless given as absolute paths (for the two
  tasks that touch `hit-backend`, a separate repo)

---

## Phase 1: Setup

- [X] T001 Run `mvn -o test -Dtest=IdentityClientTest` from `reservation/reservation/` to confirm the
      pre-existing `IdentityClient` groundwork (`reservation/reservation/src/main/java/com/rezhub/reservation/infrastructure/IdentityClient.java`)
      still compiles and passes before extending the chain that depends on it

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The field additions every user story depends on. Nothing in this phase is itself a
user-visible behavior change — every field defaults to empty until Phase 3 populates it. This phase
must be complete and compiling before any story-specific work begins.

- [X] T002 Add `identityUserId: Optional<String>` field to `OriginRequestContext` in
      `reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/OriginRequestContext.java`
- [X] T003 [P] Update `MatrixEndpoint`'s `OriginRequestContext` construction to pass `Optional.empty()`
      for the new field in `reservation/reservation/src/main/java/com/rezhub/reservation/api/MatrixEndpoint.java`
- [X] T004 [P] Update `BookingTools.directOrigin`'s `OriginRequestContext` construction to pass
      `Optional.empty()` for the new field in
      `reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingTools.java`
- [X] T005 Add `identityUserId`/`senderExternalId` (`Optional<String>`) fields to `ReservationState`;
      default both to `Optional.empty()` in `initiate()`; add `withIdentityUserId(Optional<String>)` and
      `withSenderExternalId(Optional<String>)` builder methods; append the two new fields to every
      existing `with*` method's positional record reconstruction, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationState.java`
- [X] T006 Add `identityUserId`/`senderExternalId` (`Optional<String>`) fields to
      `ReservationEvent.Inited`, updating its `@JsonCreator`/`@JsonProperty` wiring to match, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEvent.java`
- [X] T007 Add `identityUserId`/`senderExternalId` (`Optional<String>`) fields to
      `ReservationEntity.Init`; persist them into the `Inited` event inside `init()`; set them on state
      via the new `with*` methods in `applyEvent`'s `Inited` case, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java`
      (depends on T005, T006)
- [X] T008 In the same file, confirm `isReplayOfSameRequest()` does **not** compare
      `identityUserId`/`senderExternalId`, and add a one-line comment explaining why (a retry whose
      resolved identity differs between attempts, e.g. because `identity` recovered mid-retry, must
      still count as a safe replay of the same booking — see plan.md's "deliberate correctness note"),
      in `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java`
      (depends on T007)
- [X] T009 [P] Add `identityUserId`/`senderExternalId` (`Optional<String>`) fields to
      `ReservationSubmission` in
      `reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/ReservationSubmission.java`
- [X] T010 Update `CourtBookingWorkflow.book()` to populate the two new `ReservationSubmission` fields
      from `origin.identityUserId()` and `origin.senderExternalId()` in
      `reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/CourtBookingWorkflow.java`
      (depends on T002, T009)
- [X] T011 Update `ReservationGatewayAkka.submit()` to thread the two new `ReservationSubmission`
      fields into the `ReservationEntity.Init` command in
      `reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/ReservationGatewayAkka.java`
      (depends on T007, T009)

Two call sites the plan initially missed, fixed as part of this phase: `BookingEndpoint.java`'s direct
`ReservationEntity.Init` construction (no identity involved on that path — both new fields
`Optional.empty()`), and `TelegramEndpoint.java`'s own `OriginRequestContext` construction (given a
placeholder `Optional.empty()` with a `// TODO(US1)` marker, replaced with the real call in T019/T020
below). Five pre-existing test call sites needed the same mechanical update to keep compiling.

**Checkpoint**: `mvn -o compile` succeeds for the whole module; all pre-existing tests still pass
unchanged (`ReservationEntityTest` 10/10, `IdentityClientTest` 6/6). No observable behavior has
changed yet — every new field is always empty until Phase 3.

---

## Phase 2.5: Architectural correction — thread real origin through to bookCourt (discovered during Phase 3 prep)

**Why this exists**: Tracing the actual path from `TelegramEndpoint` to a persisted `Reservation`
revealed that `BookingAgent` doesn't call `CourtBookingWorkflow.book()` directly — it hands the
conversation to an LLM, which calls the `bookCourt` **tool** on `BookingTools`. That method rebuilt
its own `OriginRequestContext` from scratch via a `directOrigin()` helper that hardcoded
`Optional.empty()` for identity, because `BookingTools` was a single instance shared across every
concurrent chat session (built once in `Bootstrap.java`) — it had no way to know the current request's
resolved `identityUserId`/`senderExternalId`. Without this fix, everything Phase 2 built would compile
and "work" narrowly, but the resolved identity would be silently dropped at the tool-call boundary and
never actually reach a real Telegram-driven `Reservation`.

**Fix chosen** (over the alternative of shuttling identity through LLM function-call arguments, the
way `recipientId` already does): make `BookingTools` per-request instead of a shared singleton.
`BookingAgent` now constructs a fresh `BookingTools` inside `chat()`, carrying that request's real
`OriginRequestContext` (already resolved with identity by the time `chat()` runs) as instance state.
`checkAvailability`/`bookCourt`/`cancelReservation` all use `this.origin` directly now — the
now-redundant `directOrigin()` reconstruction is deleted entirely, not just patched. Confirmed safe by
reading both consumers of `origin` downstream (`BookingContextResolverAkka` only reads
`origin.attributes()`; `CourtBookingWorkflow.checkAvailability`/`cancel` don't reference `origin` at
all) — the only observable side effect is `ReservationState.originSystem()` becoming the real source
(`"telegram"`/`"matrix"`) instead of the hardcoded `"direct"` for agent-driven bookings, which does not
collide with the one place that branches on `originSystem` (`DelegatingServiceAction`, which only
checks for `"hit"`).

- [X] T012 Add an `OriginRequestContext origin` field to `BookingTools`, add it as a 4th constructor
      parameter, and document why (per-request, not shared) in
      `reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingTools.java`
- [X] T013 Replace all three `directOrigin(...)` call sites in `checkAvailability`, `bookCourt`, and
      `cancelReservation` with the `origin` field, then delete the now-dead `directOrigin()` helper, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingTools.java` (depends on
      T012)
- [X] T014 Change `BookingAgent`'s constructor to take `BookingApplicationService`,
      `ReservationGatewayAkka`, and `ComponentClient` instead of a pre-built `BookingTools`; construct a
      fresh `BookingTools` inside `chat()` using `request.origin()`, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/agent/BookingAgent.java` (depends on
      T012)
- [X] T015 Update `Bootstrap.java`'s `DependencyProvider` to stop building/registering a shared
      `BookingTools` singleton, and instead register `BookingApplicationService` and
      `ReservationGatewayAkka` directly (now needed by `BookingAgent`'s new constructor), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/Bootstrap.java` (depends on T014)
- [X] T016 Update `BookingServiceTest.java`'s direct `new BookingTools(...)` construction to pass a 4th
      `null` argument for `origin` (early-exit validation paths never reach it), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/agent/BookingServiceTest.java`
      (depends on T012)

**Checkpoint**: `mvn -o compile`/`test-compile` succeed; `ReservationEntityTest` 10/10,
`IdentityClientTest` 6/6, `BookingServiceTest` 9/9 all still pass. `bookCourt` now actually receives
the real, identity-resolved origin instead of a reconstructed empty one.

---

## Phase 3: User Story 1 - Every Telegram sender gets a stable, durable identity (Priority: P1) 🎯 MVP

**Goal**: A Telegram sender's real id resolves to a durable `identity` `userId` on first contact, no
verification step required, and that `userId` is persisted on the resulting reservation.

**Independent Test**: Send two Telegram messages from the same account (different chats/facilities if
possible), completing a booking each time. Fetch both reservations' persisted state directly and
confirm they carry the same `identityUserId`. Fully verifiable against a local `identity` service, no
Hit or payments code involved.

### Tests for User Story 1

- [ ] T017 [P] [US1] Add unit test case(s) to `ReservationEntityTest.java` asserting `init()` with a
      populated `identityUserId`/`senderExternalId` persists both onto state, and that
      `isReplayOfSameRequest` still treats a retry with a *different* resolved `identityUserId` as a
      safe replay (per T008), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/reservation/ReservationEntityTest.java`
- [ ] T018 [P] [US1] Create `TelegramEndpointIntegrationTest.java` covering: first-contact resolution
      mints and persists a new `identityUserId` on the resulting reservation; a second message from the
      same sender (different chat) resolves to and persists the *same* `identityUserId` on its
      reservation, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/TelegramEndpointIntegrationTest.java`

### Implementation for User Story 1

- [ ] T019 [US1] Inject `HttpClientProvider` into `TelegramEndpoint`'s constructor and construct an
      `IdentityClient` from it, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/TelegramEndpoint.java`
- [ ] T020 [US1] In `TelegramEndpoint.onUpdate`, when `senderExternalId` is non-blank, call
      `identityClient.resolveOrCreate("TELEGRAM", senderExternalId, Optional.empty())` and pass the
      result into the new `OriginRequestContext` `identityUserId` field (replacing the T002-era
      placeholder `Optional.empty()`), in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/TelegramEndpoint.java` (depends
      on T019, T002)

**Checkpoint**: User Story 1 is fully functional and independently testable — a Telegram sender's
identity is resolved and durably persisted on their reservation, and (per Phase 2.5) actually reaches
the real booking, not just `OriginRequestContext`. This is also, by construction, the MVP: everything
else in this feature verifies properties of what this phase already built.

---

## Phase 4: User Story 2 - The Telegram webhook never fails because of the identity service (Priority: P1)

**Goal**: Confirm — and complete the one remaining gap in — the fail-open guarantee. Most of this is
already true by construction: `IdentityClient.resolveOrCreate` already swallows every failure mode and
returns `Optional.empty()` (built and unit-tested pre-spec), and `TelegramEndpoint` (Phase 3) just
passes that `Optional` straight through with no additional error handling. What's missing is the one
edge case from spec.md that needs an explicit guard.

**Independent Test**: Point the `identity` client at an unreachable or error-returning endpoint, run a
full Telegram booking conversation end-to-end, and confirm it completes exactly as it would with
`identity` healthy — the resulting reservation just has no `identityUserId`.

### Tests for User Story 2

- [ ] T021 [US2] Add a case to `TelegramEndpointIntegrationTest.java` pointing identity resolution at
      an unreachable/error-returning target and asserting the booking still completes with no
      `identityUserId` on the resulting reservation, and a case asserting a message with no `from` field
      results in no resolution attempt and no `identityUserId`, in
      `reservation/reservation/src/test/java/com/rezhub/reservation/api/TelegramEndpointIntegrationTest.java`
      (same file as T018 — sequential, not parallel)

### Implementation for User Story 2

- [ ] T022 [US2] Guard `TelegramEndpoint.onUpdate` to skip the identity resolution call entirely when
      `msg.from()` is null or the sender id is blank, in
      `reservation/reservation/src/main/java/com/rezhub/reservation/api/TelegramEndpoint.java` (depends
      on T020)

**Checkpoint**: Fail-open behavior is verified end-to-end, including the no-`from`-field edge case.
`identity` being unreachable never blocks or delays a Telegram booking.

---

## Phase 5: User Story 3 - A reservation carries the requester's resolved identity (Priority: P2)

**Goal**: Confirm the resolved identity is genuinely durable — readable after the original request has
finished, not just visible during it. No new production code: Phase 3 already persists the field via
`ReservationEntity`'s state; this phase exists to prove that property explicitly, per spec.md's own
framing of US3 as "not about a currently-visible behavior."

**Independent Test**: Complete a Telegram booking with `identity` healthy, then — in a separate,
later call — fetch the reservation's state directly and confirm `identityUserId` is present.

### Tests for User Story 3

- [ ] T023 [US3] Add a test asserting that a separate, later `ReservationEntity::getReservation` query
      (not the original request) returns the persisted `identityUserId`, and that a reservation created
      when resolution failed has no `identityUserId` but does have `senderExternalId` (FR-008), in
      `reservation/reservation/src/test/java/com/rezhub/reservation/reservation/ReservationEntityTest.java`

**Checkpoint**: All three user stories are independently verified — resolution (US1), fail-open (US2),
and durable persistence (US3) — even though US1 and US3 deliver via the same underlying code path by
design.

---

## Final Phase: Polish & Cross-Cutting Concerns

**Purpose**: FR-006 doc reconciliation and closeout — deliberately sequenced last, after the code
shape is settled, per the decision made earlier in this session not to front-load doc edits before
implementation might still change them.

- [ ] T024 [P] Reconcile `hit-backend/docs/cross-product-identity.md`'s Open Items and Stage 3
      description to reflect the final design (auto-mint via Telegram, persisted on the Reservation,
      raw-id-for-backfill), replacing the currently-stale "fix TelegramEndpoint, wire resolveOrCreate"
      framing with what was actually decided and built, in
      `/Users/max/code/hit/hit-backend/docs/cross-product-identity.md`
- [ ] T025 [P] Reconcile `rez/docs/payments-cancellation-waitlist-design.md`'s `PlayerPaymentProfile`
      identity section, replacing commit `f5c2833`'s "Telegram drops out of the identity story
      entirely" framing with the resolved account (Telegram auto-mints an unlinked identity from first
      contact; explicit linking — via Rez's future own sign-in, or a Hit-authenticated link — is a
      separate, still-not-built step for cross-product recognition), in
      `/Users/max/code/rez/docs/payments-cancellation-waitlist-design.md`
- [ ] T026 Run `mvn -o verify` from `reservation/reservation/` to confirm all new and existing tests
      pass together
- [ ] T027 Execute `quickstart.md`'s manual verification steps end-to-end against a locally running
      stack, per `specs/001-telegram-identity-resolution/quickstart.md`
- [ ] T028 Update `spec.md`'s Status field from `Draft` to a closed/complete status once T026 and T027
      both pass, in `specs/001-telegram-identity-resolution/spec.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories (nothing compiles against
  the new fields until this phase is done)
- **Architectural correction (Phase 2.5)**: Depends on Phase 2 — also blocks Phase 3, discovered while
  preparing it (see Phase 2.5's own "Why this exists")
- **User Story 1 (Phase 3)**: Depends on Phase 2.5. This is the MVP.
- **User Story 2 (Phase 4)**: Depends on Phase 3 (`TelegramEndpoint` must already call
  `resolveOrCreate` before its failure mode can be tested/guarded)
- **User Story 3 (Phase 5)**: Depends on Phase 3 (persistence already exists; this phase only verifies
  it). Independent of Phase 4.
- **Polish (Final Phase)**: Depends on Phases 3–5 being complete — the doc reconciliation
  (T024/T025) needs the real, implemented shape to describe accurately

### Within Each Phase

- Tests before implementation in Phases 3–5 (write first, confirm they fail, then implement)
- Domain/entity fields (T005–T008) before the orchestration layer that threads them (T009–T011)
- Within Phase 2.5: T012 before T013/T014/T016 (all depend on the new constructor parameter existing);
  T015 depends on T014 (Bootstrap must match BookingAgent's new constructor signature)

### Parallel Opportunities

- T003 and T004 (different files, both mechanical call-site updates)
- T005, T006, T009 (three different files, no dependency between them — though T007 depends on both
  T005 and T006 completing first)
- T017 and T018 (different test files)
- T024 and T025 (different repos entirely)

---

## Parallel Example: Foundational Phase

```
Task: "Add identityUserId/senderExternalId fields to ReservationState in reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationState.java"
Task: "Add identityUserId/senderExternalId fields to ReservationEvent.Inited in reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEvent.java"
Task: "Add identityUserId/senderExternalId fields to ReservationSubmission in reservation/reservation/src/main/java/com/rezhub/reservation/orchestration/ReservationSubmission.java"
```

## Parallel Example: Polish Phase

```
Task: "Reconcile hit-backend/docs/cross-product-identity.md per FR-006"
Task: "Reconcile rez/docs/payments-cancellation-waitlist-design.md per FR-006"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup), Phase 2 (Foundational), and Phase 2.5 (architectural correction) — all
   required, not optional; nothing compiles or actually delivers identity without them
2. Complete Phase 3 (User Story 1)
3. **STOP and VALIDATE**: run T017/T018, confirm both pass
4. This is a deployable MVP — Telegram senders get durable identities

### Incremental Delivery

1. Setup + Foundational + Phase 2.5 → compiles, zero behavior change, but the plumbing is now actually
   correct end-to-end
2. Phase 3 (US1) → identity resolution + persistence live → MVP
3. Phase 4 (US2) → fail-open + edge case guard verified
4. Phase 5 (US3) → durability verified (no new code, confirms Phase 3's own claim)
5. Polish → docs reconciled, full `mvn verify`, quickstart run, spec closed out

### Note on "parallel team strategy"

Given how tightly coupled Phases 3–5 are (same code path, verification-heavy later phases), this
feature is not a good candidate for splitting across multiple developers working truly independently
— Phase 2/2.5 are hard sequential gates, and Phases 4/5 are thin verification passes over Phase 3's
work, not separately substantial implementation efforts. One implementer working through phases
1→2→2.5→3→4→5→Polish in order is the natural shape here.

---

## Notes

- [P] tasks touch different files with no unfinished dependency between them
- [Story] labels trace each task back to spec.md's user stories
- Commit after each phase (Setup, Foundational, Phase 2.5, US1, US2, US3, Polish) — matches this
  project's established discipline of committing after each logical phase rather than one giant diff
- `mvn compile` before every commit, per this project's established practice
- Phase 2.5 is a real example of why: it was only discovered by tracing the actual code path before
  writing Phase 3's tests, not by following the original plan.md/tasks.md literally. Tracing the real
  call chain before implementing is worth doing even when a plan already exists.
