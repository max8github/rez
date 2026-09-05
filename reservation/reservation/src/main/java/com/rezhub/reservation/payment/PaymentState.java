package com.rezhub.reservation.payment;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The economic record for a single reservation's payment lifecycle.
 *
 * <p>This feature (Phase 1 of the payments design) only ever drives {@code NONE -> AUTHORIZED},
 * {@code AUTHORIZED -> CAPTURED}, and {@code {NONE, AUTHORIZED} -> FAILED}. {@code VOIDED} and
 * {@code REFUNDED} are declared so a later phase needs no state-enum migration, but nothing in this
 * feature produces them (see {@code PaymentEntity}).
 *
 * @param resourceId denormalized from the reservation at first touch — see {@code PaymentEvent}'s doc
 * @param dateTime   denormalized from the reservation at first touch — see {@code PaymentEvent}'s doc
 */
public record PaymentState(
    String paymentId,
    State state,
    Optional<String> resourceId,
    Optional<LocalDateTime> dateTime,
    Optional<String> stripePaymentIntentId,
    long amountCents,
    String currency,
    String facilityConnectedAccountId,
    long applicationFeeCents,
    Optional<String> stripeChargeId,
    Optional<String> failureReason
) {

    public enum State { NONE, AUTHORIZED, CAPTURED, VOIDED, REFUNDED, FAILED }

    public static PaymentState initiate(String paymentId) {
        return new PaymentState(paymentId, State.NONE, Optional.empty(), Optional.empty(),
            Optional.empty(), 0L, "", "", 0L, Optional.empty(), Optional.empty());
    }

    public PaymentState withAuthorized(String resourceId, LocalDateTime dateTime, String stripePaymentIntentId,
                                        long amountCents, String currency, String facilityConnectedAccountId,
                                        long applicationFeeCents) {
        return new PaymentState(paymentId, State.AUTHORIZED, Optional.of(resourceId), Optional.of(dateTime),
            Optional.of(stripePaymentIntentId), amountCents, currency, facilityConnectedAccountId,
            applicationFeeCents, stripeChargeId, Optional.empty());
    }

    public PaymentState withCaptured(String stripeChargeId) {
        return new PaymentState(paymentId, State.CAPTURED, resourceId, dateTime, stripePaymentIntentId,
            amountCents, currency, facilityConnectedAccountId, applicationFeeCents,
            Optional.of(stripeChargeId), Optional.empty());
    }

    public PaymentState withFailed(String resourceId, LocalDateTime dateTime, String reason) {
        return new PaymentState(paymentId, State.FAILED, Optional.of(resourceId), Optional.of(dateTime),
            stripePaymentIntentId, amountCents, currency, facilityConnectedAccountId, applicationFeeCents,
            stripeChargeId, Optional.of(reason));
    }
}
