# Rez Docs

This folder holds the durable architecture docs, active runbooks/plans, and archived historical design notes for `rez`.

## Current status

Current source of truth: Rez is a generic booking engine centered on resources, reservations, locking correctness, and a thin orchestration layer.

Current code reality: some older target-state design notes and transport-specific experiments still exist in the repo. Treat the current-state architecture docs as authoritative, and treat archived design notes as historical unless a document explicitly says otherwise.

## Reference docs

These are the stable, current-state architecture docs:

- [Rez System Overview](./reference/rez-system-overview.md)
- [Reservation Locking](./reference/reservation-locking.md)
- [Reservation Booking Flow](../reservation/reservation/docs/booking-flow.md)
- [Reservation FSM](../reservation/reservation/docs/fsm.md)
- [Conceptual Orchestration Overview](./conceptual-orchestration-overview.md)

These documents should explain:

- how the system is structured right now
- where locking correctness lives
- how the main booking flow works
- which components own orchestration, reservation state, and side effects

The conceptual orchestration overview is the main layering doc. If it conflicts with the current-state overview, the current-state overview wins.

## Working docs

These are active, time-bound docs that track operations or upcoming changes:

- [DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md)
- [deployment.md](./deployment.md)
- [deployment-standalone.md](./deployment-standalone.md)
- [provisioning.md](./provisioning.md)
- [facility-provisioning-runbook.md](./facility-provisioning-runbook.md)
- [quick-notes-runbook.md](./quick-notes-runbook.md)
- [user-onboarding.md](./user-onboarding.md)

## Archive docs

These are historical design notes, scratch material, or superseded migration docs kept only for context:

- [archive/my_notes.md](./archive/my_notes.md)
- [archive/notion-export-relevance.md](./archive/notion-export-relevance.md)

## Conventions

When adding a new doc:

1. Put long-lived current-state architecture explanation in a reference doc.
2. Put active runbooks, deployment steps, provisioning, and implementation planning in a working doc.
3. Put superseded target-state designs, scratch notes, and historical research in `archive/`.
4. Cross-link instead of duplicating the same explanation in several places.
