# Feature Specification: Telegram Identity Resolution

- **Feature Branch**: `001-telegram-identity-resolution`
- **Created**: 2026-09-03
- **Status**: Draft
- **Input**: User description: "Rez's TelegramEndpoint currently captures the real Telegram sender id (`senderExternalId`) but never uses it downstream — only the chat-scoped recipientId hash gets persisted. Fix this by wiring TelegramEndpoint to call the shared `identity` service's `resolveOrCreate` on every incoming message, and thread the resolved identity userId into `OriginRequestContext` as a new optional field so it's available to future consumers. First-contact resolution via Telegram alone mints a fresh, Telegram-only identity — it does not prove the sender is the same person as any existing Hit account. Cross-product recognition only happens after a separate, explicit one-time link flow (out of scope here). Fail-open throughout. Also reconcile hit-backend's cross-product-identity.md and Rez's payments-cancellation-waitlist-design.md, which currently describe two different, conflicting versions of this plan."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every Telegram sender gets a stable, durable identity (Priority: P1)

Today, Rez only knows a Telegram conversation by a hash of `(botToken, chatId)` — a *chat*, not a *person*. A Telegram sender's real per-account id (Telegram's own, authenticated by Telegram's platform when the message is delivered) resolves to a durable identity via the shared `identity` service on first contact — no separate verification step (e.g. a Google/Apple sign-in) is required to obtain one. That identity is stored on the reservation itself when a booking is made, not just used transiently during the conversation, so it remains meaningful after the fact (e.g. "show me every booking this person has made").

This identity is deliberately **unlinked** to any other product's account by default. Proving that a Telegram sender is the same person as an existing Hit account requires a separate, explicit one-time link flow (the bot sends a Hit-authenticated link; logging in there proves ownership and adds Telegram as a second auth method on the existing Hit identity) — that flow is not part of this feature (see Out of Scope).

**Why this priority**: This is the concrete, load-bearing prerequisite the rest of cross-product identity (Stage 3+) depends on — nothing else in Rez can key off "this person" until this exists, and it isn't real until it's actually retrievable later, not just computed-and-discarded per message.

**Independent Test**: Send two Telegram messages from the same Telegram account — in different chats, and/or after a service restart so no in-memory state survives — each resulting in a booking. Both reservations carry the same `identity` `userId`. Fully verifiable against a locally running `identity` service, no Hit or payments code involved.

**Acceptance Scenarios**:

1. **Given** a Telegram user messaging a configured facility's bot for the first time and completing a booking, **When** the reservation is created, **Then** the shared `identity` service has minted a new `userId` for that Telegram sender, with no prior verification step, and the reservation is stored with that `userId` attached.
2. **Given** a Telegram user who has messaged and booked before, **When** they book again (same or different chat, same or different facility), **Then** the new reservation carries the *same* `userId` as their earlier one.

---

### User Story 2 - The Telegram webhook never fails because of the identity service (Priority: P1)

`identity` is a separate service Rez depends on but does not own. This feature adds exactly one new outbound call to it inside the Telegram webhook handler. That call failing (unreachable, error response, timeout) must never throw, never block, never delay the reply to the player, and never be surfaced to them — the booking conversation must proceed exactly as it does today, just without a resolved identity attached to the resulting reservation.

**Why this priority**: Equal priority to User Story 1 — resolving identity is worthless if getting it wrong ever breaks the one thing Rez actually exists to do (let someone book a court). This mirrors the fail-open principle already established for Hit's own `identity` integration (`hit-backend` spec 006).

**Scope note — this is intentionally narrow**: nothing in this feature depends on payments, because Rez has no payments feature built yet. This requirement says only that *this one HTTP call* fails safe. It makes no promise about a future feature that adds a real payment dependency on `identity` being reachable (e.g. a not-yet-built `PlayerPaymentProfile`) — that future feature must define its own failure semantics for identity-service downtime; this spec does not pre-decide that for it.

**Independent Test**: Point Rez's `identity` client at an unreachable or error-returning endpoint, then run a full Telegram booking conversation end-to-end. The booking succeeds exactly as it would with `identity` healthy; the resulting reservation simply has no `identityUserId` attached.

**Acceptance Scenarios**:

1. **Given** the `identity` service is unreachable, **When** a Telegram message arrives and results in a booking, **Then** the booking conversation proceeds normally, no error is surfaced to the player, and the reservation is created without an `identityUserId`.
2. **Given** the `identity` service returns a non-success response, **When** a Telegram message arrives, **Then** the failure is logged (for operators) but not retried inline and does not delay the reply to the player.

---

### User Story 3 - A reservation carries the requester's resolved identity (Priority: P2)

A reservation record gains the requester's resolved `identity` `userId` (when one was available at booking time), alongside the chat-scoped `recipientId` it already stores. This is what actually makes User Story 1's identity useful rather than a discarded implementation detail: it gives a real, durable foothold for later features (e.g. a future `PlayerPaymentProfile`, or an explicit account-link flow) to find "this person's" reservations, without this feature needing to build any new query capability itself.

**Why this priority**: Lower priority than 1/2 because nothing consumes this value yet — this story is about not discarding the resolved identity the moment the request finishes, not about a currently-visible player-facing behavior.

**Independent Test**: Complete a Telegram booking with `identity` healthy, then inspect the resulting reservation's stored data directly (e.g. via existing entity lookup) and confirm the resolved `identityUserId` is present and durable — not just visible in a log line during the original request.

**Acceptance Scenarios**:

1. **Given** a resolved identity for an incoming Telegram message that results in a booking, **When** the reservation is created, **Then** the stored reservation includes that `identityUserId`.
2. **Given** no identity could be resolved (resolution failed, or the sender had no identifiable Telegram `from`), **When** the reservation is created, **Then** it is stored successfully without an `identityUserId` — but, when a sender was identifiable, still with their raw Telegram sender id, so the reservation is not permanently unrecoverable (see FR-008 and Out of Scope).

### Edge Cases

- What happens when the same Telegram sender messages two *different* facilities' bots (different bot tokens, same underlying Telegram account)? → Must still resolve to the same `userId` — identity is per-person, not per-facility or per-bot.
- What happens when a Telegram update has no `from` field (e.g. a channel post) or an empty sender id? → No identity resolution call is made; existing behavior (ignore/log) is unchanged.
- What happens on the very first message ever received by a freshly deployed Rez instance, before `identity` has ever seen this or any Telegram sender? → Same as any first-contact case: `identity` mints a new record; no special-casing needed.
- What happens when someone already has a Hit account (an existing Google/Apple-linked `userId`) and messages Rez's bot without ever going through the explicit link flow? → They get a second, separate, Telegram-only `userId`; no automatic connection to their Hit account is made or attempted (see Out of Scope).
- What happens to a reservation created while `identity` was unreachable? → It has no `identityUserId`, but it does retain the raw Telegram sender id it was resolved from, so it is not permanently unattributable — a future backfill process (not built here) could re-resolve it once `identity` is healthy again.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST resolve a stable identity for every incoming Telegram message that has a sender, using the sender's real Telegram user id (not the chat-scoped identifier already in use).
- **FR-002**: System MUST reuse the same resolved identity across multiple messages from the same Telegram sender, regardless of which chat or which facility's bot they are messaging through.
- **FR-003**: Identity resolution MUST be fail-open, scoped narrowly to this feature: a failure (unreachable service, non-success response, timeout) MUST be logged, MUST NOT throw or block the Telegram webhook's response, and MUST result in the reservation being created without an `identityUserId` rather than blocking reservation creation. This requirement does not extend to any future feature that adds a hard payment dependency on `identity` being reachable — such a feature must define its own failure semantics separately.
- **FR-004**: The resolved identity (when available) MUST be persisted onto the resulting Reservation record, alongside the existing `recipientId` — not merely available transiently during request handling — so it remains queryable after the fact. Reservations created when no identity could be resolved MUST persist successfully without one, identical to today's behavior.
- **FR-005**: A first-contact resolution for a given Telegram sender MUST NOT be treated, anywhere in documentation or behavior, as proof that the sender is the same person as any existing account in another product. Establishing that requires a separate, explicit linking step, which this feature does not implement.
- **FR-006**: The two existing design documents that describe this identity-resolution story (hit-backend's cross-product identity design, and Rez's own payments/booking design doc) MUST be reconciled to describe one consistent account of what first-contact Telegram resolution does and does not establish, replacing their current conflicting accounts.
- **FR-007**: System MUST create (not merely look up) a new identity via the shared `identity` service on a Telegram sender's first contact — no separate verification step (e.g. Google or Apple sign-in) is required before a reservation can carry that sender's resolved identity.
- **FR-008**: The Telegram sender's raw external id MUST be persisted onto the resulting Reservation record unconditionally, whenever a sender was identifiable — regardless of whether identity resolution itself succeeded. This is what makes a reservation left without an `identityUserId` (due to a resolution failure) recoverable later by a future backfill process, rather than permanently unattributable. This feature persists the raw id for that purpose but does not implement the backfill process itself.

### Key Entities

- **Resolved Identity**: The stable, cross-message identifier for a real person, obtained from the shared `identity` service for a given Telegram sender. Carries no proof of linkage to any other product's account on its own.
- **Telegram Sender**: The real per-person identifier Telegram provides for the author of an incoming message, as distinct from the chat the message was sent in.
- **Reservation**: Gains an optional resolved identity (`identityUserId`), present whenever resolution succeeded at booking time, and the raw Telegram sender id it was resolved from, persisted unconditionally whenever a sender was identifiable — alongside its existing chat-scoped `recipientId`.

## Out of Scope

- **Explicit cross-product account linking** — the bot sending a Hit-authenticated link, the person logging into Hit, and `identity` adding Telegram as a second auth method on their existing Hit identity. This feature's identities are designed to support that flow later; the flow itself is not built here.
- **Account merging** — even once linked, no migration of a Telegram-only identity's existing reservations onto the linked Hit identity is designed or built. Someone who books under a Telegram-only identity before linking has that history left behind under the old `userId`. Accepted as a deferred gap given every product is still pre-launch.
- **A dedicated "reservations by identity" query or view** — `identityUserId` is persisted on the Reservation record so it's queryable via existing data-access paths, but no new View is built until a concrete feature needs one.
- **A backfill process for orphaned reservations** — reservations created while `identity` was unreachable retain the raw Telegram sender id specifically so a future process could re-resolve and fill in their `identityUserId` later. That process itself (whether a scheduled job, an on-demand trigger, or something else) is not designed or built here.
- **Telegram webhook secret-token hardening** — using Telegram's `secret_token` mechanism to further authenticate webhook calls is a good idea, but a general webhook-security improvement unrelated to identity resolution specifically.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The same real Telegram user resolves to the same identity 100% of the time across repeat contact, verified across at least chat and facility variation.
- **SC-002**: 0% of Telegram booking conversations fail or visibly degrade when the identity-resolution dependency is unavailable — booking success rate with `identity` down is identical to booking success rate with `identity` healthy.
- **SC-003**: 100% of the two existing design documents' descriptions of Telegram identity resolution agree with each other and with actual behavior after this change — no reviewer can find a contradiction between them.
- **SC-004**: 100% of reservations booked while identity resolution succeeded have a durably queryable `identityUserId` attached, without requiring any new dedicated query or view to be built.
- **SC-005**: 100% of reservations from an identifiable Telegram sender — whether or not identity resolution succeeded at the time — retain enough information to be attributed to an identity later, without needing to replay a lost Telegram message.
