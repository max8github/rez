package com.rezhub.reservation.payment;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.timer.TimerScheduler;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.FacilityState;
import com.rezhub.reservation.infrastructure.StripeService;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Fires at a reservation's commitment cutoff to create the Stripe hold (FR-007), and again at the
 * resolution point to capture it (FR-008). One class, two methods — see plan.md's Scale/Scope note:
 * this is deliberately not two separate components.
 */
@Component(id = "commitment-cutoff-timed-action")
public class CommitmentCutoffTimedAction extends TimedAction {
    private static final Logger log = LoggerFactory.getLogger(CommitmentCutoffTimedAction.class);

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;
    private final StripeService stripeService;

    public CommitmentCutoffTimedAction(ComponentClient componentClient, TimerScheduler timerScheduler,
                                        StripeService stripeService) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
        this.stripeService = stripeService;
    }

    public record HoldAttempt(String reservationId, String resourceId, LocalDateTime slotStart, int attemptNumber) {}

    public Effect attemptHold(HoldAttempt command) {
        String reservationId = command.reservationId();
        Optional<PricingPolicy> effectivePolicy = PricingPolicyResolver.resolve(componentClient, command.resourceId());
        if (effectivePolicy.isEmpty()) {
            log.warn("No PricingPolicy resolvable for reservation {} at commitment cutoff — skipping hold creation", reservationId);
            return effects().done();
        }
        PricingPolicy policy = effectivePolicy.get();

        ReservationState reservation = componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::getReservation)
            .invoke();
        Optional<String> identityUserId = reservation.identityUserId();

        PlayerPaymentProfileState profile = identityUserId
            .map(userId -> componentClient.forKeyValueEntity(userId)
                .method(PlayerPaymentProfileEntity::getProfile)
                .invoke())
            .orElse(null);

        String facilityId = PricingPolicyResolver.resolveFacilityId(componentClient, command.resourceId());
        String connectedAccountId = null;
        if (facilityId != null && !facilityId.isBlank()) {
            FacilityState facilityState = componentClient.forEventSourcedEntity(facilityId)
                .method(FacilityEntity::getState)
                .invoke();
            connectedAccountId = facilityState.stripeConnectedAccountId().orElse(null);
        }

        if (profile == null || !profile.hasPaymentMethod() || connectedAccountId == null) {
            log.warn("Reservation {} reached commitment cutoff without a usable payment method/connected account", reservationId);
            failAndCancel(reservationId, command.resourceId(), command.slotStart(), "no_payment_method_or_connected_account");
            return effects().done();
        }

        long amountCents = policy.priceCents();
        long applicationFeeCents = Math.round(amountCents * policy.commissionFraction());

        StripeService.HoldResult hold;
        try {
            hold = stripeService.createAndConfirmHold(amountCents, policy.currency(),
                profile.stripeCustomerId().orElseThrow(), profile.defaultPaymentMethodId().orElseThrow(),
                reservationId);
        } catch (Exception e) {
            // FR-016's retry-vs-notify classification (transient vs. card-specific) lands here in a
            // later phase (US4) — Phase 3/US1 only implements the happy path.
            log.error("Hold creation failed for reservation {}: {}", reservationId, e.getMessage());
            failAndCancel(reservationId, command.resourceId(), command.slotStart(), e.getMessage());
            return effects().done();
        }

        componentClient.forEventSourcedEntity(reservationId)
            .method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(reservationId, command.resourceId(), command.slotStart(),
                hold.paymentIntentId(), amountCents, policy.currency(), connectedAccountId, applicationFeeCents));

        LocalDateTime now = LocalDateTime.now();
        Duration delay = Duration.between(now, command.slotStart());
        if (delay.isNegative()) {
            delay = Duration.ZERO;
        }
        log.info("Hold authorized for reservation {}; resolution point in {}", reservationId, delay);
        timerScheduler.createSingleTimer(
            "resolution-point-" + reservationId,
            delay,
            componentClient.forTimedAction()
                .method(CommitmentCutoffTimedAction::captureHold)
                .deferred(reservationId));

        return effects().done();
    }

    public Effect captureHold(String reservationId) {
        PaymentState payment = componentClient.forEventSourcedEntity(reservationId)
            .method(PaymentEntity::getPayment)
            .invoke();
        if (payment.state() != PaymentState.State.AUTHORIZED) {
            log.debug("Payment {} is {} at resolution point, not AUTHORIZED — nothing to capture", reservationId, payment.state());
            return effects().done();
        }

        String connectedAccountId = payment.facilityConnectedAccountId();
        double facilityFraction = payment.amountCents() == 0 ? 0
            : 1.0 - (double) payment.applicationFeeCents() / payment.amountCents();

        try {
            String chargeId = stripeService.capturePaymentIntent(payment.stripePaymentIntentId().orElseThrow(), reservationId);
            stripeService.createTransferFromCharge(chargeId, facilityFraction, connectedAccountId, reservationId);
            componentClient.forEventSourcedEntity(reservationId)
                .method(PaymentEntity::capture)
                .invoke(new PaymentEntity.Capture(chargeId));
            log.info("Captured payment {} at resolution point", reservationId);
        } catch (Exception e) {
            log.error("Capture failed for reservation {}: {}", reservationId, e.getMessage());
        }
        return effects().done();
    }

    private void failAndCancel(String reservationId, String resourceId, LocalDateTime slotStart, String reason) {
        componentClient.forEventSourcedEntity(reservationId)
            .method(PaymentEntity::fail)
            .invoke(new PaymentEntity.Fail(resourceId, slotStart, reason));
        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::cancelRequest)
            .invoke();
    }
}
