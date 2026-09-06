# Contracts: Payment Core

New external HTTP interfaces this feature adds. Existing endpoints (`TelegramEndpoint`, etc.) are
unchanged in their request/response shape — only their internal behavior changes (FR-005/FR-012 gates
inside `CourtBookingWorkflow.book()`).

`BookingEndpoint`'s `POST /bookings` is the one exception with an observable response-shape change:
unlike `CourtBookingWorkflow`'s path, it carries no player identity at all (see research.md #10), so it
gains only the FR-012 facility-payability check, not FR-005's player-side check. A request targeting an
unpayable facility (a `PricingPolicy` configured but Stripe onboarding incomplete) now returns `400 Bad
Request` instead of `200`/`201` with a `COLLECTING` reservation that would later fail unrecoverably at
its commitment cutoff with no one to notify.

## `POST /webhooks/stripe`

New endpoint, `StripeWebhookEndpoint` (mirrors `hit-backend`'s endpoint of the same name/path shape).

- **Request**: raw Stripe webhook payload (`application/json`), `Stripe-Signature` header required.
- **Response**: `200 OK` always (Stripe requires 2xx to stop retrying; failures are logged, not
  surfaced as non-2xx, except an invalid/missing signature → `400`).
- **Events handled**: `setup_intent.succeeded`, `payment_method.attached`, `payment_intent.succeeded`,
  `payment_intent.payment_failed`. Unhandled event types are logged at `debug` and ignored.
- **Idempotency**: required (FR-006) — see data-model.md's `StripeWebhookEndpoint` section for how this
  falls out of `PaymentEntity`'s own command-handler state guards.
- **ACL**: `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` — Stripe calls this from the
  public internet, same as `hit-backend`'s equivalent.

## `PUT /facility/{facilityId}/pricing-policy`

New endpoint method on the existing `FacilityEndpoint`.

- **Request body**: `{ "priceCents": 5000, "currency": "eur", "commissionFraction": 0.10, "commitmentWindowHours": 72 }`
- **Response**: `200 OK` on success; `400` if `commitmentWindowHours` exceeds the FR-011 safety cap.
- **ACL**: same as `FacilityEndpoint`'s existing methods (`Acl.Principal.ALL` — no dedicated admin auth
  layer exists yet in this codebase; matches existing convention, not a new gap introduced here).

## `PUT /facility/{facilityId}/stripe-connected-account`

New endpoint method on `FacilityEndpoint`, setting the facility's `acct_...` id once Connect onboarding
completes (onboarding-link generation itself is out of scope for this spec unless a task turns out to
need it — see tasks.md).

- **Request body**: `{ "connectedAccountId": "acct_..." }`
- **Response**: `200 OK`.

## `PUT /resource/{resourceId}/pricing-policy`

New endpoint method on the existing `ResourceEndpoint`, setting the per-resource override (FR-003).
Same request/response shape as the facility-level endpoint above.

## `GET /facility/{facilityId}` and `GET /resource/{resourceId}` (existing, response shape extended)

Both existing `Facility`/`Resource` API DTOs gain the new fields (`pricingPolicy`,
`stripeConnectedAccountId` on facility; `pricingPolicyOverride` on resource) so an admin caller can
read back what was configured. Additive only — no existing field removed or renamed, so this is not a
breaking change for any existing caller.
