# Quickstart: Verifying Payment Core

Manual verification steps once the implementation lands, covering spec.md's four user stories. Uses
Stripe **test mode** throughout — never run these against a live Stripe account.

## Prerequisites

- Local `reservation` service running with `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET` set to Stripe
  test-mode values (see `hit-backend`'s `docs/local-dev.md` for the equivalent `stripe listen`
  forwarding setup — Rez's local stack needs the same pattern pointed at `/webhooks/stripe`).
- A test facility provisioned with `PricingPolicy` (price, commission, a short `commitmentWindow` —
  e.g. a few minutes, so you don't have to wait days to see a hold fire) and a Stripe test-mode
  connected account (`acct_...`) set via the new pricing-policy endpoints (research.md #8).
- A Stripe test card (`4242 4242 4242 4242`) for completing the hosted card-collection link.

## User Story 1 — returning player, no extra steps

1. Complete User Story 2 once first, so a `PlayerPaymentProfile` already exists for your test player.
2. Book a court through Telegram exactly as before. Confirm the conversation looks identical to a
   pre-payments booking — no card prompt.
3. Wait for the commitment cutoff (short in test config). Confirm via `PaymentEntity`'s state (direct
   entity lookup, or logs) that it moved to `AUTHORIZED`, with no message sent to the player.
4. Wait for the resolution point (`slotStart` — set a near-future test slot). Confirm `PaymentEntity`
   moves to `CAPTURED`, and (via Stripe test-mode dashboard) that a Transfer was created to the test
   connected account.
5. Book another court far enough out that its commitment cutoff hasn't arrived, then cancel
   immediately. Confirm no `PaymentEntity` was ever created for that reservation.

## User Story 2 — first-time card collection

1. From a Telegram account with no existing `PlayerPaymentProfile`, attempt to book a court.
2. Confirm: no reservation is created (check `ReservationLookupEndpoint`/logs — no new
   `ReservationEntity` for this attempt), and the bot's reply includes a Stripe-hosted
   card-collection link instead of a booking confirmation.
3. Open the link, complete it with the test card.
4. Confirm `StripeWebhookEndpoint` received `setup_intent.succeeded` and `PlayerPaymentProfile` for
   this player's `userId` now has a `stripeCustomerId` and `defaultPaymentMethodId`.
5. Send the same booking request again. Confirm it now succeeds normally (User Story 1's flow).

## User Story 3 — facility pricing configuration

1. Configure `PricingPolicy` on a facility with no existing policy (research.md #8's endpoint). Book a
   resource on it and confirm the hold amount matches.
2. Set a per-resource override on one resource with a different price/commitment window. Book that
   specific resource and confirm the override values apply, not the facility default.
3. Attempt to set a `commitmentWindow` clearly over the Stripe-safe cap (e.g. 30 days). Confirm the
   request is rejected (FR-011), not silently accepted.

## User Story 4 — payment failure paths

**Transient failure**: point `StripeService` at an invalid/unreachable endpoint temporarily (or use a
Stripe test-mode network-failure simulation if available), trigger a commitment-cutoff hold, and
confirm: retries happen (check logs for repeated attempts with backoff) with no player-facing message,
before eventually — once real Stripe access is restored, or once retries exhaust — resolving one way
or the other.

**Card failure**: use a Stripe test card known to trigger `authentication_required` or a decline (see
Stripe's test card list) as the player's saved payment method, trigger the commitment cutoff, and
confirm: the player receives a re-authentication/new-card notification, a grace window opens, and if
it elapses without a successful hold, the reservation is cancelled and the court becomes bookable by
someone else again.

## Edge cases worth a manual pass

- Replay a Stripe webhook event (resend the same `payment_intent.succeeded` payload/signature twice)
  and confirm no double transfer, double capture, or `PaymentEntity` state corruption (FR-006/SC-006).
- Change a facility's `PricingPolicy` after a booking exists but before its commitment cutoff; confirm
  the eventual hold uses the *new* policy, not the one in effect at booking time (FR-013).
