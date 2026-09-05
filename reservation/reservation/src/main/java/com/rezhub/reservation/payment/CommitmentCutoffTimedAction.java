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
import com.rezhub.reservation.spi.NotificationSender;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.RateLimitException;
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

    /** Bounded per FR-016 — an operational parameter, not a protocol-level requirement (spec.md Assumptions). */
    private static final int MAX_TRANSIENT_ATTEMPTS = 5;
    /** Bounded per FR-010 — likewise an operational parameter. */
    private static final Duration GRACE_WINDOW = Duration.ofMinutes(30);
    /**
     * Bounds Akka's own automatic retry-on-failure for these scheduled calls (indefinite by default —
     * see timed-actions.html.md "Failures and retries"). Independent of MAX_TRANSIENT_ATTEMPTS, which
     * governs this class's own hand-rolled FR-016 retry counter for Stripe-specific failures — this
     * bounds the SDK's separate, lower-level retry of the scheduled call itself failing outright (e.g.
     * an unhandled exception from a componentClient call this class doesn't already catch).
     */
    private static final int TIMER_MAX_RETRIES = 3;

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;
    private final StripeService stripeService;
    private final NotificationSender notificationSender;

    public CommitmentCutoffTimedAction(ComponentClient componentClient, TimerScheduler timerScheduler,
                                        StripeService stripeService, NotificationSender notificationSender) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
        this.stripeService = stripeService;
        this.notificationSender = notificationSender;
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
            // Not a Stripe-side failure — there's nothing to retry against, so this always goes
            // straight to the notify-and-grace-window path (FR-010), never through FR-016's retry.
            log.warn("Reservation {} reached commitment cutoff without a usable payment method/connected account", reservationId);
            notifyAndScheduleGraceWindow(reservationId, reservation, "no_payment_method_or_connected_account");
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
            if (isTransient(e) && command.attemptNumber() < MAX_TRANSIENT_ATTEMPTS) {
                Duration backoff = Duration.ofSeconds((long) Math.pow(2, command.attemptNumber()));
                log.warn("Transient hold-creation failure for reservation {} (attempt {}/{}): {} — retrying in {}, no player notification",
                    reservationId, command.attemptNumber(), MAX_TRANSIENT_ATTEMPTS, e.getMessage(), backoff);
                timerScheduler.createSingleTimer(
                    "commitment-cutoff-" + reservationId + "-attempt-" + (command.attemptNumber() + 1),
                    backoff,
                    TIMER_MAX_RETRIES,
                    componentClient.forTimedAction()
                        .method(CommitmentCutoffTimedAction::attemptHold)
                        .deferred(new HoldAttempt(reservationId, command.resourceId(), command.slotStart(), command.attemptNumber() + 1)));
                return effects().done();
            }
            // Card-specific from the first attempt, or a transient failure with retries exhausted —
            // either way, FR-010's notify-and-grace-window path applies (FR-016).
            log.error("Hold creation failed for reservation {} (attempt {}): {}", reservationId, command.attemptNumber(), e.getMessage());
            notifyAndScheduleGraceWindow(reservationId, reservation, e.getMessage());
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
            TIMER_MAX_RETRIES,
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

    /**
     * FR-016: distinguishes a Stripe/network-availability failure (worth retrying automatically, no
     * player involvement) from a card-specific one (needs the player's attention). Everything that
     * isn't recognizably transient is treated as card-specific/terminal — matching FR-016's framing
     * ("...rather than a card-specific decline").
     */
    private boolean isTransient(Exception e) {
        return e instanceof ApiConnectionException || e instanceof RateLimitException || e instanceof ApiException;
    }

    /**
     * FR-010: notifies the player (when there's a channel to notify on — a reservation with no
     * resolved identity, e.g. from {@code BookingEndpoint}'s direct path, has none; see spec.md's Edge
     * Cases) and opens a bounded grace window before giving up. Does not touch {@code PaymentEntity}
     * here — it stays in {@code NONE} so a successful hold could still be authorized during the grace
     * window; {@link #onGraceWindowExpired} is what finalizes a genuine failure.
     */
    private void notifyAndScheduleGraceWindow(String reservationId, ReservationState reservation, String reason) {
        String recipientId = reservation.recipientId();
        if (recipientId != null && !recipientId.isBlank()) {
            String text = "We couldn't process payment for your upcoming booking — please update your payment method to keep it. "
                + "If we don't hear back soon, this reservation will be released.";
            notificationSender.send(recipientId, text)
                .whenComplete((result, error) -> {
                    if (error != null) log.error("Failed to send payment-failure notification for reservation {}: {}", reservationId, error.getMessage());
                });
        } else {
            log.warn("Reservation {} has no notification channel (no resolved identity) — grace window still applies, but the player is not notified", reservationId);
        }

        LocalDateTime now = LocalDateTime.now();
        Duration timeUntilResolution = Duration.between(now, reservation.dateTime());
        Duration grace = GRACE_WINDOW.compareTo(timeUntilResolution) < 0 ? GRACE_WINDOW : timeUntilResolution;
        if (grace.isNegative()) {
            grace = Duration.ZERO;
        }
        log.info("Reservation {} payment failed ({}) — grace window of {} before cancellation", reservationId, reason, grace);
        timerScheduler.createSingleTimer(
            "grace-window-" + reservationId,
            grace,
            TIMER_MAX_RETRIES,
            componentClient.forTimedAction()
                .method(CommitmentCutoffTimedAction::onGraceWindowExpired)
                .deferred(reservationId));
    }

    /**
     * FR-010's terminal step: if no hold was successfully authorized during the grace window, the
     * payment is marked FAILED and the reservation is cancelled, releasing the court.
     */
    public Effect onGraceWindowExpired(String reservationId) {
        PaymentState payment = componentClient.forEventSourcedEntity(reservationId)
            .method(PaymentEntity::getPayment)
            .invoke();
        if (payment.state() == PaymentState.State.AUTHORIZED || payment.state() == PaymentState.State.CAPTURED) {
            log.info("Reservation {} payment reached {} during its grace window — no cancellation needed", reservationId, payment.state());
            return effects().done();
        }
        if (payment.state() != PaymentState.State.FAILED) {
            String resourceId = payment.resourceId().orElse("");
            LocalDateTime slotStart = payment.dateTime().orElse(LocalDateTime.now());
            componentClient.forEventSourcedEntity(reservationId)
                .method(PaymentEntity::fail)
                .invoke(new PaymentEntity.Fail(resourceId, slotStart, "grace_window_expired"));
        }
        log.info("Reservation {} grace window expired with no successful hold — cancelling", reservationId);
        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::cancelRequest)
            .invoke();
        return effects().done();
    }
}
