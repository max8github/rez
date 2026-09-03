# Feature Specification: Telegram Identity Resolution

**Feature Branch**: `001-telegram-identity-resolution`
**Created**: 2026-09-03
**Status**: Draft
**Input**: User description: "Rez's TelegramEndpoint currently captures the real Telegram sender id (senderExternalId) but never uses it downstream — only the chat-scoped recipientId hash gets persisted. Fix this by wiring TelegramEndpoint to call the shared `identity` service's resolveOrCreate on every incoming message, and thread the resolved identity userId into OriginRequestContext as a new optional field so it's available to future consumers. First-contact resolution via Telegram alone mints a fresh, Telegram-only identity — it does not prove the sender is the same person as any existing Hit account. Cross-product recognition only happens after a separate, explicit one-time link flow (out of scope here). Fail-open throughout. Also reconcile hit-backend's cross-product-identity.md and Rez's payments-cancellation-waitlist-design.md, which currently describe two different, conflicting versions of this plan."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every Telegram sender gets a stable identity (Priority: P1)

Today, Rez only knows a Telegram conversation by a hash of `(botToken, chatId)` — a *chat*, not a *person*. Two different people messaging the same facility's bot in the same chat (e.g. a shared family phone) are indistinguishable to Rez, and the same person messaging from two different chats looks like two different people. Every message from a real Telegram user should resolve to one stable, durable identifier for that person, obtained from the shared `identity` service, independent of which chat or facility they're talking to.

**Why this priority**: This is the concrete, load-bearing prerequisite the rest of cross-product identity (Stage 3+) depends on — nothing else in Rez can key off "this person" until this exists. It's also independently useful to Rez today (per-person booking history, dedup) with no dependency on any other product.

**Independent Test**: Send two Telegram messages from the same Telegram account, in different chats (or after a service restart, so no in-memory state survives). Both calls resolve to the same `identity` `userId`. Can be fully verified against a locally running `identity` service without any Hit or payments code involved.

**Acceptance Scenarios**:

1. **Given** a Telegram user messaging a configured facility's bot for the first time, **When** the message is received, **Then** Rez resolves (and the `identity` service mints) a new `userId` for that Telegram sender.
2. **Given** a Telegram user who has messaged before, **When** they send another message (same or different chat, same facility or a different one), **Then** Rez resolves the *same* `userId` as before.
3. **Given** two different Telegram users messaging in the same Telegram chat (e.g. a shared/group context), **When** each sends a message, **Then** each resolves to their own distinct `userId`.

---

### User Story 2 - Booking flow is unaffected when `identity` is unavailable (Priority: P1)

`identity` is a separate service Rez depends on but does not own. If it's down, slow, or returns an error, a player trying to book a court over Telegram must still be able to do so — identity resolution is a value-add, not a hard dependency for Rez's core booking function.

**Why this priority**: Equal priority to User Story 1 — resolving identity is worthless if getting it wrong ever breaks a booking. This is a direct carry-over of the fail-open principle already established for Hit's own `identity` integration (`hit-backend` spec 006).

**Independent Test**: Point Rez's `identity` client at an unreachable or error-returning endpoint, then run a full Telegram booking conversation end-to-end. The booking succeeds exactly as it would with `identity` healthy; only the identity-resolution step is silently skipped.

**Acceptance Scenarios**:

1. **Given** the `identity` service is unreachable, **When** a Telegram message arrives, **Then** the booking conversation proceeds normally and no error is surfaced to the player.
2. **Given** the `identity` service returns a non-success response, **When** a Telegram message arrives, **Then** the failure is logged (for operators) but not retried inline and does not delay the reply to the player.

---

### User Story 3 - Resolved identity is available to future consumers, without overclaiming what it proves (Priority: P2)

The resolved `userId` needs to be threaded to wherever a future feature (e.g. `PlayerPaymentProfile`, not built yet) would need it, without that future feature — or anyone reading the code — assuming a first-contact resolution already proves the Telegram sender is the same person as an existing Hit account. That stronger claim requires a separate, explicit linking step that is out of scope for this spec.

**Why this priority**: Lower priority than 1/2 because nothing consumes this value yet — this story is about not laying a trap for the next feature that does, not about a currently-visible user-facing behavior.

**Independent Test**: Inspect the data made available per Telegram message (in logs, or in a debugger/test) and confirm the resolved identity is present and traceable, alongside documentation making clear a first-contact resolution is not proof of cross-product identity.

**Acceptance Scenarios**:

1. **Given** a resolved identity for an incoming Telegram message, **When** the request is handed off to the rest of the booking flow, **Then** the resolved identity travels with it in a form later steps can read.
2. **Given** no explicit account-linking has ever happened for a given Telegram sender, **When** their identity is resolved, **Then** nothing in the system behaves as though that identity is connected to any other product's account.

### Edge Cases

- What happens when the same Telegram sender messages two *different* facilities' bots (different bot tokens, same underlying Telegram account)? → Must still resolve to the same `userId` — identity is per-person, not per-facility or per-bot.
- What happens when a Telegram update has no `from` field (e.g. a channel post) or an empty sender id? → No identity resolution call is made; existing behavior (ignore/log) is unchanged.
- What happens on the very first message ever received by a freshly deployed Rez instance, before `identity` has ever seen this or any Telegram sender? → Same as any first-contact case: `identity` mints a new record; no special-casing needed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST resolve a stable identity for every incoming Telegram message that has a sender, using the sender's real Telegram user id (not the chat-scoped identifier already in use).
- **FR-002**: System MUST reuse the same resolved identity across multiple messages from the same Telegram sender, regardless of which chat or which facility's bot they are messaging through.
- **FR-003**: Identity resolution MUST be fail-open: any failure to resolve (unreachable service, error response, timeout) MUST be logged and MUST NOT prevent the booking conversation from proceeding, MUST NOT be retried synchronously, and MUST NOT be surfaced to the player.
- **FR-004**: The resolved identity (when available) MUST be made available to the rest of the request-handling flow that already carries the sender's other origin details, so future features can read it without re-resolving.
- **FR-005**: A first-contact resolution for a given Telegram sender MUST NOT be treated, anywhere in documentation or behavior, as proof that the sender is the same person as any existing account in another product. Establishing that requires a separate, explicit linking step, which this feature does not implement.
- **FR-006**: The two existing design documents that describe this identity-resolution story (hit-backend's cross-product identity design, and Rez's own payments/booking design doc) MUST be reconciled to describe one consistent account of what first-contact Telegram resolution does and does not establish, replacing their current conflicting accounts.

### Key Entities

- **Resolved Identity**: The stable, cross-message identifier for a real person, obtained from the shared `identity` service for a given Telegram sender. Carries no proof of linkage to any other product's account on its own.
- **Telegram Sender**: The real per-person identifier Telegram provides for the author of an incoming message, as distinct from the chat the message was sent in.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The same real Telegram user resolves to the same identity 100% of the time across repeat contact, verified across at least chat and facility variation.
- **SC-002**: 0% of Telegram booking conversations fail or visibly degrade when the identity-resolution dependency is unavailable — booking success rate with `identity` down is identical to booking success rate with `identity` healthy.
- **SC-003**: 100% of the two existing design documents' descriptions of Telegram identity resolution agree with each other and with actual behavior after this change — no reviewer can find a contradiction between them.
