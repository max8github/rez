# Phase 0 Research: Telegram Identity Resolution

No open `NEEDS CLARIFICATION` markers remain in `spec.md` or `plan.md` — every fork was resolved
during spec review (see `checklists/requirements.md`'s Notes for the full trail). This document
consolidates those decisions in the Decision/Rationale/Alternatives format for the planning record,
plus two small implementation-level lookups that shape task breakdown.

## Decision: reuse `IdentityClient` as-is, no changes

**Rationale**: Already built and tested (`infrastructure/IdentityClient.java`,
`IdentityClientTest.java`, 8 passing cases) as pre-spec groundwork, mirroring hit-backend's
`hit.infrastructure.IdentityClient` fail-open contract exactly. Its `resolveOrCreate(String provider,
String externalId, Optional<String> email)` signature already fits this feature's call shape:
`resolveOrCreate("TELEGRAM", senderExternalId, Optional.empty())` — Telegram provides no email claim.

**Alternatives considered**: None seriously — building a second client would duplicate tested,
working code for no benefit.

## Decision: `identity`'s `Provider` enum already has `TELEGRAM`

**Rationale**: Confirmed by reading `identity/src/main/java/identity/domain/Provider.java` —
`TELEGRAM` is already a valid value, alongside `GOOGLE`, `APPLE`, `MANUAL`, `STRIPE`, `REVOLUT`. No
change needed in the `identity` repo for this feature.

**Alternatives considered**: N/A — this is a fact check, not a design choice.

## Decision: auto-mint on first Telegram contact (FR-007)

**Rationale**: Structurally symmetric with how `identity` already treats every other provider —
`resolveOrCreate` for GOOGLE/APPLE also auto-creates on first contact, trusting that the caller
already verified the external claim before calling. For Telegram, the equivalent trust boundary is
Telegram's own platform: `message.from.id` is authenticated by Telegram's servers when a message is
delivered, not forgeable by another Telegram user. Requiring a separate Google/Apple step before any
identity exists would reintroduce a sign-in requirement Rez's Telegram-bot design was built to avoid.

**Alternatives considered**: Require Google/Apple verification before minting any `userId`
(rejected — defeats the low-friction premise of a Telegram-bot booking flow); silently match by name
or other heuristics (rejected outright — violates the design's "explicit linking, never silent
matching" principle).

## Decision: persist `identityUserId` and raw `senderExternalId` on `ReservationEntity`, not just `OriginRequestContext`

**Rationale**: `OriginRequestContext` is transient (per-request, discarded after the HTTP call
completes) — persisting only there would make User Story 1's stated value ("a durable per-person
key") false, since nothing would remain queryable afterward. Persisting the raw sender id
unconditionally (FR-008), even when resolution fails, is what makes an orphaned reservation
recoverable later by a future backfill process, rather than permanently unattributable.

**Alternatives considered**: Transient-only (rejected — see above); persisting `identity`'s other
`User` attributes (email, name) onto the Reservation (rejected — that would be real data duplication,
not a foreign-key reference; discussed and rejected in-session, see conversation history).

## Decision: fail-open scope is exactly the one HTTP call, not the whole booking/payment flow

**Rationale**: Rez has no payment feature built yet, so "booking succeeds when `identity` is down" is
narrow and cheap today — it only means the new outbound call must not throw/block. This must not be
read as a guarantee extending to a future payment-dependent flow (e.g. `PlayerPaymentProfile`
lookups), which will need its own failure semantics when it's built.

**Alternatives considered**: A broader fail-open claim covering future payment flows too (rejected —
overpromises something this feature has no way to guarantee, and no payment code exists yet to test
against).

## Implementation-level lookup: `ReservationsByRecipientView` accesses `Inited` fields by name

**Finding**: `ReservationsByRecipientView.ReservationsByRecipientUpdater.onEvent` pattern-matches
`ReservationEvent.Inited e` and reads `e.recipientId()`, `e.reservation()...` — named accessors, not
positional destructuring. Confirmed by reading the file directly.

**Implication**: Adding `identityUserId`/`senderExternalId` fields to the `Inited` record is
compile-safe against this view with zero changes to it. The view simply won't surface the two new
fields in its `Entry` row — consistent with the spec's Out of Scope (no new/enriched identity-aware
view until something concrete needs one).

## Implementation-level lookup: `isReplayOfSameRequest` must exclude the two new fields

**Finding**: `ReservationEntity.isReplayOfSameRequest()` treats a retried `init()` call as a safe
replay only if several fields match exactly (`dateTime`, `durationMinutes`, `resourceIds`,
`recipientId`, `originSystem`, `emails`).

**Implication**: The two new fields must **not** be added to that comparison. If `identity` was down
on a first `init()` attempt and reachable on a retried one, the resolved `identityUserId` could
legitimately differ between attempts — that must still count as a safe replay of the same booking,
not a rejected "cannot be reinitialized" collision. Flagged explicitly as a task-breakdown item.
