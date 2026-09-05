# Data Model: Payment Core

Phase 1 output for `002-payment-core`. New/changed state only — existing fields not touched by this
feature are omitted for brevity (see the referenced source files for full current shape).

## PaymentEntity (new, Event Sourced)

Package: `com.rezhub.reservation.payment`. Entity id = the owning reservation's `reservationId` (see
research.md #3 — no separate id generation).

```java
public record PaymentState(
    String paymentId,                    // == reservationId
    State state,                         // NONE, AUTHORIZED, CAPTURED, VOIDED, REFUNDED, FAILED
    Optional<String> stripePaymentIntentId,
    long amountCents,
    String currency,
    String facilityConnectedAccountId,
    long applicationFeeCents,
    Optional<String> stripeChargeId,     // set once captured — needed for createTransferFromCharge
    Optional<String> failureReason
) {
    public enum State { NONE, AUTHORIZED, CAPTURED, VOIDED, REFUNDED, FAILED }
}
```

**Events** (`PaymentEvent`, sealed interface, `@TypeName` per record):

- `HoldAuthorized(paymentId, reservationId, stripePaymentIntentId, amountCents, currency, facilityConnectedAccountId, applicationFeeCents)` — `NONE → AUTHORIZED`. Applied when off-session confirmation succeeds (FR-007).
- `HoldCaptured(paymentId, stripeChargeId)` — `AUTHORIZED → CAPTURED`. Applied at the resolution point (FR-008).
- `HoldCreationFailed(paymentId, reason)` — `{NONE, AUTHORIZED} → FAILED`. Applied when retries are exhausted or a genuine card-specific error occurs (FR-010/FR-016). Not the same as `VOIDED` — no funds were ever reserved, or a reserved hold could not be captured; either way, nothing to void, nothing to refund.

**Command handlers implemented in Phase 1** (per FR-017 — no `void()`/`refund()` yet):

- `authorize(Authorize command)` — `NONE → AUTHORIZED`. Rejects if already in a non-`NONE` state (idempotency guard against a duplicate Timer fire).
- `capture(Capture command)` — `AUTHORIZED → CAPTURED`. Rejects from any other state.
- `fail(Fail command)` — `{NONE, AUTHORIZED} → FAILED`. Rejects if already `CAPTURED` (a captured payment cannot retroactively fail).

**Not implemented in Phase 1**: `void()`, `refund()`. `VOIDED`/`REFUNDED` remain valid `State` enum
values (so Phase 2 and a future admin/dispute-refund feature need no state-enum migration) but no
command produces them yet.

## PlayerPaymentProfile (new, Key Value Entity)

Package: `com.rezhub.reservation.payment`. Entity id = the shared `identity` service's `userId`.

```java
public record PlayerPaymentProfileState(
    String userId,
    Optional<String> stripeCustomerId,
    Optional<String> defaultPaymentMethodId
) {
    public static PlayerPaymentProfileState empty(String userId) {
        return new PlayerPaymentProfileState(userId, Optional.empty(), Optional.empty());
    }

    public boolean hasPaymentMethod() {
        return stripeCustomerId.isPresent() && defaultPaymentMethodId.isPresent();
    }
}
```

**Commands**: `linkCustomer(String stripeCustomerId)`, `setDefaultPaymentMethod(String paymentMethodId)`
— both direct state updates (Key Value Entity, no event log), invoked from `StripeWebhookEndpoint` on
`setup_intent.succeeded` / `payment_method.attached`.

## PricingPolicy (new value type, embedded — not its own entity)

```java
public record PricingPolicy(
    long priceCents,
    String currency,
    double commissionFraction,      // Rez's cut, e.g. 0.10
    Duration commitmentWindow
) {
    public void validate() {
        // FR-011: reject a commitmentWindow that risks exceeding Stripe's ~7-day
        // authorization capture limit — exact cap TBD in tasks.md, comfortably under 7 days.
    }
}
```

**On `FacilityState`** (`com.rezhub.reservation.customer.facility`): gains
`Optional<PricingPolicy> pricingPolicy` and `Optional<String> stripeConnectedAccountId`, each with a
`with*()` builder method, following the existing pattern (`withBotToken`, `withAdminUserIds`).
New `FacilityEvent` variants: `PricingPolicySet(String facilityId, PricingPolicy policy)`,
`StripeConnectedAccountSet(String facilityId, String accountId)`.

**On `ResourceState`** (`com.rezhub.reservation.resource`): gains `Optional<PricingPolicy>
pricingPolicyOverride`, following the existing `with*()` pattern (`withExternalRef`,
`withBookingGranularityMinutes`). New `ResourceEvent` variant: `PricingPolicyOverrideSet(String
resourceId, PricingPolicy policy)`.

**Resolution rule** (FR-003, FR-013): given a resource, its effective `PricingPolicy` is
`resource.pricingPolicyOverride().orElseGet(() -> facility.pricingPolicy())` — evaluated fresh at the
moment the commitment-cutoff hold is created (FR-013), not cached at booking time.

## ReservationState (touched)

Package: `com.rezhub.reservation.reservation`. Gains one field, following the existing `with*()`
pattern already used for `identityUserId`/`senderExternalId`:

```java
Optional<String> paymentId   // == reservationId once commitment-cutoff processing begins (see research.md #3)
```

`ReservationEntity.isReplayOfSameRequest()` is **not** touched — `paymentId` is derived, not part of
what makes two booking attempts "the same booking" (same reasoning already applied to
`identityUserId`/`senderExternalId`, see that method's existing doc comment).

## StripeWebhookEndpoint (new)

Package: `com.rezhub.reservation.api`. `POST /webhooks/stripe`, structurally mirroring
`hit-backend`'s `StripeWebhookEndpoint` (signature verification via `Webhook.constructEvent`, raw-JSON
parsing to dodge Stripe-CLI/SDK API-version skew, `HttpResponses.ok()` in no-op mode when
`STRIPE_WEBHOOK_SECRET` is unset). Routes:

- `setup_intent.succeeded`, `payment_method.attached` → `PlayerPaymentProfileEntity::linkCustomer` / `::setDefaultPaymentMethod`, resolved via the event's `metadata.userId` (set when the Checkout/Payment Link session is created — see quickstart.md).
- `payment_intent.succeeded`, `payment_intent.payment_failed` → idempotent reconciliation against `PaymentEntity` (FR-006's duplicate/replay requirement): re-delivering an event whose target state is already reached is a no-op (`authorize`/`capture`/`fail` command handlers already reject a same-or-later-state transition, so this is a natural consequence of the entity's own idempotency guard, not new logic).

## SlotPaymentView (new, Akka View)

Package: `com.rezhub.reservation.payment`. Keyed by `resourceId + dateTime`, per FR-014 — exists and
is populated in this feature, with no consumer built here (Phase 2's rescue-refund lookup is the first
real consumer).

```
SELECT * FROM slot_payment_view WHERE resourceId = :resourceId AND dateTime = :dateTime
```

Sourced from `PaymentEntity`'s `HoldAuthorized`/`HoldCaptured`/`HoldCreationFailed` events (for payment
state) joined with `ReservationEntity`'s `Fulfilled` event (for the `resourceId + dateTime` key itself,
since `PaymentEntity` alone doesn't know which slot it belongs to — that lives on the reservation).
