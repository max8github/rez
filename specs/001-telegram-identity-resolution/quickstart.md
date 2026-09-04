# Quickstart: Verifying Telegram Identity Resolution

Manual verification steps once the implementation lands, covering the spec's three user stories.

## Prerequisites

- Local `identity` service running (see `identity` repo's own local-dev instructions).
- Local `reservation` service running with a configured Telegram bot token pointed at a test facility
  (see `rez/docs/facility-provisioning-runbook.md`).

## User Story 1 — stable identity across repeat contact

1. From one Telegram account, message the bot: book a court.
2. Query `identity`'s `/internal/users/{userId}` for the `userId` that ended up on the resulting
   reservation (via `ReservationEntity::getReservation`, e.g. through `ReservationLookupEndpoint`) —
   confirm a `TELEGRAM` `AUTH` link with your Telegram id is present.
3. Send a second message from the **same** Telegram account, in a **different** chat if possible
   (or to a different facility's bot), and complete a second booking.
4. Confirm both reservations carry the identical `identityUserId`.

## User Story 2 — fail-open

1. Stop the local `identity` service (or point `IdentityClient`'s `httpClientFor("identity")` at an
   unreachable address).
2. Message the bot and complete a booking as normal.
3. Confirm: the booking completes, the player sees a normal confirmation reply, and the resulting
   reservation has no `identityUserId` — but does have the raw Telegram sender id (FR-008). Check
   service logs for a `WARN`-level `identity resolveOrCreate(...)` failure log line from
   `IdentityClient`.

## User Story 3 — persisted, not transient

1. With `identity` healthy, complete a booking.
2. Fetch the reservation's full state directly (`ReservationEntity::getReservation`, e.g. via
   `GET` on `ReservationLookupEndpoint` or a direct `EventSourcedTestKit`/admin lookup) — some time
   after the original request has finished, not just by reading logs from the original call.
3. Confirm `identityUserId` is present in the persisted state, not only visible in the original
   request's log output.

## Edge cases worth a manual pass

- Send a Telegram update with no identifiable sender (not generally producible from a real Telegram
  client — cover this one via the endpoint integration test instead, not manually).
- Message the bot for the very first time on a freshly seeded local stack (no prior `identity` state)
  — confirm a new `identity` user gets minted with no special-casing needed.
