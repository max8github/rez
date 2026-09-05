package com.rezhub.reservation.payment;

import akka.javasdk.annotations.TypeName;

import java.time.LocalDateTime;

public sealed interface PaymentEvent {

    /**
     * {@code resourceId}/{@code dateTime} are denormalized from the owning reservation here — the
     * caller ({@code CommitmentCutoffTimedAction}) always knows them before attempting a hold, and
     * stamping them at first-touch (whether the attempt succeeds or fails) is what lets
     * {@code SlotPaymentView} be sourced from {@code PaymentEntity} alone, without a cross-entity join.
     */
    @TypeName("payment-hold-authorized")
    record HoldAuthorized(String paymentId, String reservationId, String resourceId, LocalDateTime dateTime,
                          String stripePaymentIntentId, long amountCents, String currency,
                          String facilityConnectedAccountId, long applicationFeeCents) implements PaymentEvent {}

    @TypeName("payment-hold-captured")
    record HoldCaptured(String paymentId, String stripeChargeId) implements PaymentEvent {}

    @TypeName("payment-hold-creation-failed")
    record HoldCreationFailed(String paymentId, String resourceId, LocalDateTime dateTime, String reason) implements PaymentEvent {}
}
