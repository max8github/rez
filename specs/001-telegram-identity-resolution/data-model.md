# Phase 1 Data Model: Telegram Identity Resolution

Two records gain fields; no new entity, event, or component is introduced. Everything below is
additive — existing fields, existing state transitions, existing query paths are unchanged.

## `OriginRequestContext` (transient — not persisted)

`reservation/orchestration/OriginRequestContext.java`

| Field | Type | Change | Notes |
|---|---|---|---|
| `origin` | `String` | unchanged | e.g. `"telegram"`, `"matrix"`, `"direct"` |
| `senderExternalId` | `String` | unchanged | Already present — the raw per-provider sender id (Telegram user id, Matrix user id, or empty for direct) |
| `senderDisplayName` | `String` | unchanged | |
| `recipientId` | `String` | unchanged | Chat-scoped, not person-scoped — unrelated concern (see spec's framing of "notification transport vs. identity") |
| `conversationId` | `String` | unchanged | |
| `attributes` | `Map<String, String>` | unchanged | |
| **`identityUserId`** | **`Optional<String>`** | **new** | Resolved `identity` `userId`, when available. Empty for every non-Telegram origin in this feature's scope (`matrix`, `direct`) and for Telegram when resolution failed or no sender was identifiable. |

Only `TelegramEndpoint` ever populates `identityUserId` with a real value in this feature.
`MatrixEndpoint` and `BookingTools.directOrigin` pass `Optional.empty()` — out of scope for those
origins.

## `ReservationSubmission` (transient — carries booking intent into the reservation core)

`reservation/orchestration/ReservationSubmission.java`

| Field | Type | Change |
|---|---|---|
| *(existing 8 fields unchanged: `reservationId`, `recipientId`, `timezone`, `dateTime`, `durationMinutes`, `participants`, `resourceIds`, `originSystem`)* | | unchanged |
| **`identityUserId`** | **`Optional<String>`** | **new** — copied from `origin.identityUserId()` in `CourtBookingWorkflow.book()` |
| **`senderExternalId`** | **`Optional<String>`** | **new** — copied from `origin.senderExternalId()`, wrapped as `Optional` (empty string → `Optional.empty()`) |

## `ReservationEntity.Init` (command)

`reservation/reservation/ReservationEntity.java`

| Field | Type | Change |
|---|---|---|
| *(existing: `reservation`, `resourceIds`, `recipientId`, `originSystem`)* | | unchanged |
| **`identityUserId`** | **`Optional<String>`** | **new** |
| **`senderExternalId`** | **`Optional<String>`** | **new** |

`isReplayOfSameRequest()` deliberately does **not** compare these two fields (see research.md) —
they describe the requester, not what makes two booking attempts "the same booking."

## `ReservationEvent.Inited` (persisted event)

`reservation/reservation/ReservationEvent.java`

| Field | Type | Change |
|---|---|---|
| *(existing: `reservationId`, `reservation`, `resourceIds`, `recipientId`, `originSystem`)* | | unchanged |
| **`identityUserId`** | **`Optional<String>`** | **new** |
| **`senderExternalId`** | **`Optional<String>`** | **new** |

`@JsonCreator`/`@JsonProperty` wiring updated to match (existing pattern in this file already does
this for `Inited`, `SearchExhausted`, `Fulfilled`).

No other `ReservationEvent` variant changes — `SearchExhausted`, `Fulfilled`, `ReservationCancelled`,
etc. read the two new fields from `currentState()` implicitly once `Inited` has set them; they don't
need to carry the fields in their own event payload (same reasoning already applies to how those
events currently *don't* echo every state field either).

## `ReservationState` (entity state — the durable, queryable record)

`reservation/reservation/ReservationState.java`

| Field | Type | Change |
|---|---|---|
| *(existing 11 fields unchanged)* | | unchanged |
| **`identityUserId`** | **`Optional<String>`** | **new** — defaults to `Optional.empty()` in `initiate()`; set once, at `Inited`, never changed afterward |
| **`senderExternalId`** | **`Optional<String>`** | **new** — same lifecycle |

New builder methods: `withIdentityUserId(Optional<String>)`, `withSenderExternalId(Optional<String>)`,
following the existing `with*` pattern. Because this record has no builder abstraction (each `with*`
method reconstructs the full positional record), **every existing `with*` method's positional
constructor call needs the two new trailing arguments appended** — mechanical, compiler-enforced, no
behavior change to any of them.

## State transition (unchanged shape, two new fields set once)

```
INIT --Init(..., identityUserId, senderExternalId)--> COLLECTING
      (ReservationEvent.Inited persists both; ReservationState.applyEvent sets them)
      -- both fields are immutable for the rest of the reservation's lifecycle --
```

No new states, no new transitions. `identityUserId`/`senderExternalId` are set exactly once, at
`Inited`, and read (never written) by every subsequent event handler via `currentState()`.

## Validation rules

- `identityUserId` and `senderExternalId` are always `Optional` end-to-end — never a required field,
  never validated as non-empty. A reservation with both empty is a valid, ordinary reservation
  (matches today's behavior for non-Telegram origins, and Telegram when resolution fails or no
  sender was identifiable).
- No new validation is introduced. `IdentityClient.resolveOrCreate` already returns `Optional.empty()`
  on any failure (network, non-2xx, deserialization) — the caller (`TelegramEndpoint`) does not
  need its own additional failure handling; it just passes the `Optional` straight through.
