# Specification Quality Checklist: Payment Core

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-05
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

First-pass draft (2026-09-05) deliberately left no `[NEEDS CLARIFICATION]` markers, resolving the
design doc's four Phase-1-relevant Open Questions (#2, #5, #7, #8) as informed defaults recorded in
the Assumptions section instead — each is a genuinely open business/ops decision (grace-period
duration, admin surface choice) rather than something that changes Phase 1's scope or architecture,
so documenting an assumption was preferred over blocking on a marker. These are exactly the kind of
forks expected to surface during `/akka.clarify` review, per the task's framing — flagged here rather
than silently decided so review can override any of them.

**`/akka.clarify` review (2026-09-05)** surfaced one real fork the first pass got wrong: re-reading the
design doc's Code Mapping section ("`CourtBookingWorkflow` gains a card-on-file check **before
submitting** a booking") against the first draft's FR-009 (which assumed a reservation existed and
needed a timeout/cancel) revealed a contradiction. Resolved: booking is deferred entirely until a
first-time player has a card on file — nothing is ever locked, so FR-009 simplified from "cancel after
grace period" to "no reservation is ever created for that attempt." This also simplified User Story 2
and removed a whole class of cleanup logic Phase 1 doesn't actually need. Three more forks were
resolved in the same pass: transient Stripe/network errors at the commitment cutoff get automatic
retry with backoff before falling into the player-notification path (new FR-016); `PaymentEntity`'s
`VOIDED`/`REFUNDED` states are declared but get no command handlers in Phase 1, since nothing calls
them yet (new FR-017); and the Stripe-onboarding-completeness check (FR-012) moved from "checked at
the commitment cutoff" to "checked at booking time," mirroring the same pattern just established for
the player-side card check. All four are recorded under spec.md's new `## Clarifications` section.
Re-verified: all checklist items still pass after these edits.

**`/akka.analyze` review (2026-09-05)**, run against spec.md/plan.md/tasks.md together, surfaced one
CRITICAL finding: FR-005/FR-012's booking-time gates were planned only inside `CourtBookingWorkflow`,
but `docs/reference/rez-system-overview.md`'s own architecture diagram shows `BookingEndpoint`'s direct
`POST /bookings` path bypasses `CourtBookingWorkflow` entirely. Reading `BookingEndpoint.java` directly
confirmed it further: `BookingRequest` carries no player-identity field at all (already hardcoded to
`Optional.empty()` for `identityUserId`, predating this feature), though it does carry enough to derive
a facility. Resolved by narrowing scope precisely rather than either ignoring the gap or overclaiming a
fix: FR-005 (needs player identity) is now explicitly scoped to identity-bearing entry points only;
FR-012 (needs only a facility, derivable either way) now explicitly applies to both entry points. A new
Out of Scope item and Edge Case record `BookingEndpoint`'s missing payer-identity concept as a named,
pre-existing limitation this feature surfaces but does not create or resolve. Also fixed in the same
pass: FR-002's wording didn't match the resolved `paymentId`-timing decision already recorded in
research.md #3 (paymentId is set when commitment-cutoff processing *begins*, not once a hold is
successfully authorized) — spec text now matches. All re-verified: checklist items still pass.

Two things mentioned in the source design doc are intentionally *not* restated as requirements here,
per the task's explicit scoping instruction: the exact Stripe routing decision (destination charges,
Rez as merchant of record) is treated as already-decided upstream (doc §1, "decided, not open") and
referenced rather than re-litigated; and anything belonging to Phase 2 (rescue refund) or Phase 3
(waiting list) is named only in Out of Scope, never designed.

One deliberate scope split worth surfacing at review time: FR-009 (card-collection-never-completed)
and FR-010 (off-session hold failure at the commitment cutoff) are two different failure points in the
same booking lifecycle, treated as distinct requirements rather than one "payment failure" bucket —
per the task's explicit instruction not to conflate them. Both converge on the same terminal outcome
(cancel and release the court) but are triggered at different times and, per FR-010, involve a player
notification the FR-009 path does not (a first-time player who never gave a card was never promised
one would be silently retried).
