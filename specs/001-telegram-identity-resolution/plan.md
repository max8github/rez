# Implementation Plan: Telegram Identity Resolution

**Branch**: `001-telegram-identity-resolution` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/001-telegram-identity-resolution/spec.md`

## Summary

Wire `TelegramEndpoint` to resolve every incoming Telegram sender against the shared `identity`
service, using the `IdentityClient` already built and tested (`infrastructure/IdentityClient.java`).
The resolved `userId` — and, per FR-008, the raw Telegram sender id unconditionally — get threaded
through the existing booking chain and persisted onto `ReservationEntity`'s state, alongside the
`recipientId`/`originSystem` fields that chain already carries today. No new components: this is a
field-threading change through five existing classes, plus one new constructor dependency on
`TelegramEndpoint`.

## Technical Context

**Language/Version**: Java 21, Akka SDK 3.5.x (matches existing `reservation` module)
**Primary Dependencies**: `IdentityClient` (already built, `infrastructure` package) — no new dependency
**Storage**: Akka Event Sourced Entity (`ReservationEntity`) — existing storage, two new optional fields
**Testing**: JUnit 5 + `EventSourcedTestKit` (entity unit tests), `TestKitSupport`/`httpClient` (endpoint integration tests) — matches existing conventions in this module
**Target Platform**: Akka SDK service (same as rest of `reservation` module)
**Project Type**: Single Maven module (`reservation`) — no new module
**Performance Goals**: N/A — one additional outbound HTTP call per Telegram message, already fail-open/non-blocking by construction (`IdentityClient`)
**Constraints**: Fail-open (FR-003) — a slow/unreachable `identity` must not delay the Telegram webhook's reply
**Scale/Scope**: Touches 8 existing files; 0 new components

## Constitution Check

*Gate: re-checked after Phase 1 design below.*

Against `rez`'s constitution (v1.0.0, freshly installed this session, same baseline as hit-backend's):

- **I. Akka SDK First**: No violation. All changes use existing Akka SDK primitives (`EventSourcedEntity` state/event fields, `HttpClientProvider`-backed client). No new external dependency.
- **II. Design Principles**: No violation. `IdentityClient` stays in `infrastructure`, domain records (`ReservationState`, `ReservationEvent`) stay framework-free, `OriginRequestContext` stays the existing transport-agnostic seam. Descriptive naming preserved (`identityUserId`, `senderExternalId`).
- **III. Test Coverage**: Satisfied by design — `IdentityClient` already has unit tests; this plan adds entity-level tests for the two new `ReservationState` fields and an endpoint-level fail-open test for `TelegramEndpoint`.
- **IV. Simplicity**: No violation. Deliberately minimal: no new component, no new view, no backfill mechanism (see spec's Out of Scope) — just the two fields needed to make Story 1/3 real.

**Result**: PASS. No entries needed in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-telegram-identity-resolution/
├── spec.md               # Already complete
├── plan.md               # This file
├── research.md           # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
└── tasks.md              # Phase 2 output (/akka.tasks — not created by this command)
```

No `contracts/` directory: this feature adds no new external interface. `TelegramEndpoint`'s webhook
request/response shape is unchanged (still Telegram's own `Update` JSON in, empty 200 out); the
`identity` service's `/internal/identities/resolve` contract already exists and is owned by the
`identity` repo's own spec, not redefined here.

### Source Code (repository root)

All within the existing `reservation` Maven module (`reservation/reservation/`) — no new module, no
new top-level directory. Existing single-module layout unchanged:

```text
reservation/reservation/src/main/java/com/rezhub/reservation/
├── infrastructure/
│   └── IdentityClient.java              # Already exists — reused, not modified
├── api/
│   ├── TelegramEndpoint.java            # MODIFIED — calls IdentityClient, builds new OriginRequestContext field
│   └── MatrixEndpoint.java              # MODIFIED — one-line call-site update only (new field = Optional.empty())
├── agent/
│   └── BookingTools.java                # MODIFIED — same one-line call-site update (directOrigin helper)
├── orchestration/
│   ├── OriginRequestContext.java        # MODIFIED — +1 field: identityUserId (senderExternalId already exists)
│   ├── ReservationSubmission.java       # MODIFIED — +2 fields: identityUserId, senderExternalId
│   ├── ReservationGatewayAkka.java      # MODIFIED — threads the 2 new submission fields into Init
│   └── CourtBookingWorkflow.java        # MODIFIED — builds ReservationSubmission with origin's 2 new fields
└── reservation/
    ├── ReservationEntity.java           # MODIFIED — Init command +2 fields, applyEvent(Inited) sets them
    ├── ReservationEvent.java            # MODIFIED — Inited record +2 fields (+ @JsonCreator update)
    └── ReservationState.java            # MODIFIED — +2 fields, +2 with*() builder methods, existing with*() methods updated to carry them through
```

`ReservationsByRecipientView.java` is **not modified** — it accesses `Inited` fields by name, not
positionally, so it compiles unchanged and simply doesn't surface the two new fields (matches the
spec's Out of Scope: no new/enriched view until something concrete needs one).

**Structure Decision**: Straight-line field threading through the existing single-module chain
(`TelegramEndpoint → OriginRequestContext → CourtBookingWorkflow → ReservationSubmission →
ReservationGatewayAkka → ReservationEntity.Init → ReservationEvent.Inited → ReservationState`) —
the same path `recipientId`/`originSystem` already travel today. No new component, no new module,
no architectural decision beyond "reuse the existing seam."

### One deliberate correctness note for `/akka.tasks`

`ReservationEntity.isReplayOfSameRequest()` (the crash-retry-safety check in `init()`) must **not**
compare the two new fields. It already ignores nothing booking-relevant, but if `identityUserId`
were added to that equality check, a retry where `identity` was down on attempt 1 but reachable on
attempt 2 would resolve a different `identityUserId` and get wrongly rejected as "cannot be
reinitialized" instead of treated as a safe replay. The two new fields are metadata about the
requester, not part of what makes two booking attempts "the same booking."

## Complexity Tracking

*No violations — table intentionally omitted.*
