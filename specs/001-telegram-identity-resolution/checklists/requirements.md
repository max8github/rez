# Specification Quality Checklist: Telegram Identity Resolution

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-03
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

First-pass draft (2026-09-03) had no [NEEDS CLARIFICATION] markers, but user review of that draft
surfaced two real open forks the checklist itself didn't catch:

1. Whether first-contact Telegram resolution should auto-mint an identity or require Google/Apple
   verification first — resolved: auto-mint (User Story 1, FR-007), after discussing duplicate-
   account risk and Telegram id spoofability.
2. Whether the resolved identity should persist onto the Reservation record or just live
   transiently in-request — resolved: persist (User Story 3, FR-004), since transient-only would
   have made User Story 1's stated value (a durable per-person key) untrue.

Spec revised 2026-09-03 to reflect both resolutions, narrow User Story 2/FR-003's fail-open claim
to exactly the new HTTP call (not an implied payment-survives-outage guarantee), drop an unclear
"shared chat" acceptance scenario, and add an explicit Out of Scope section (account merging,
dedicated identity-query view, webhook secret-token hardening). All checklist items re-verified
against the revised spec and still pass.

Follow-up review (2026-09-04) walked through a concrete failure example and surfaced one more gap:
a reservation created while `identity` is down had no way to be recovered later, since the raw
Telegram sender id used to make the resolution call was discarded, not stored. Resolved by adding
FR-008: persist the raw sender id on the Reservation unconditionally (whether or not resolution
succeeded), so a future backfill process — not built here — could re-resolve it later. Added as a
new Out of Scope item and SC-005. Re-verified: still passes all checklist items.

Implementation (2026-09-04, Phases 1–4) surfaced two things the spec itself didn't need to change for,
but worth recording here since they shaped what got built:

1. An architectural gap the plan missed entirely: `BookingTools` was a shared singleton with no way to
   see a request's resolved identity, so `TelegramEndpoint`'s resolution would have been silently
   dropped before reaching a real `Reservation`. Fixed by making `BookingTools` per-request
   (tasks.md Phase 2.5) — this was necessary for FR-004/FR-007 to actually hold, not optional polish.
2. A real test-environment constraint: `TestKitSupport` has no reachable `identity` and this SDK
   version can't mock `httpClientFor(...)` in-process, and this codebase's own convention (see
   `BookingAgentIntegrationTest`) deliberately doesn't test tool-call execution via mocked LLM
   responses. So "first contact mints an identity" (US1's strongest claim) is verified manually via
   `quickstart.md`, not by an automated test — documented directly in `TelegramEndpointIntegrationTest`'s
   own class doc comment rather than left implicit.

Neither changes any FR/SC in spec.md — both are implementation-level findings, recorded here and in
tasks.md rather than as spec churn.

**Closed 2026-09-04**: T027's manual verification (the one thing automated tests structurally couldn't
cover, per point 2 above) passed against a real local stack — a genuine Telegram booking minted a real
`identity` `userId` (`5d8b28c5-c37d-4a26-8a18-9677dff5542f`), and a second booking from the same
Telegram account resolved to the same `userId` (`isNew: false`, confirmed via direct `identity` curl
calls). All 28 tasks in tasks.md complete. spec.md's Status updated to CLOSED.
