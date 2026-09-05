package com.rezhub.reservation.payment;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.timer.TimerScheduler;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reacts to {@code ReservationEntity.Fulfilled} to schedule the commitment-cutoff Timer (FR-007).
 * Kept separate from {@code DelegatingServiceAction} (notification) — single responsibility, see
 * research.md #5.
 *
 * <p>A facility with no {@code PricingPolicy} at all is genuinely free-to-book (matches Rez's
 * pre-payments behavior) — this consumer does nothing for such a reservation: no {@code paymentId} is
 * recorded, no Timer is scheduled, no {@code PaymentEntity} is ever created.
 */
@Component(id = "payment-scheduling-action")
@Consume.FromEventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class PaymentSchedulingAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentSchedulingAction.class);

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public PaymentSchedulingAction(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    public Effect on(ReservationEvent.Fulfilled event) {
        String reservationId = event.reservationId();
        String resourceId = event.resourceId();
        LocalDateTime slotStart = event.reservation().dateTime();

        Optional<PricingPolicy> effectivePolicy = PricingPolicyResolver.resolve(componentClient, resourceId);
        if (effectivePolicy.isEmpty()) {
            log.debug("Reservation {} has no PricingPolicy in effect — no payment processing scheduled", reservationId);
            return effects().done();
        }

        Duration commitmentWindow = effectivePolicy.get().commitmentWindow();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = slotStart.minus(commitmentWindow);
        if (cutoff.isBefore(now)) {
            cutoff = now;
        }
        Duration delay = Duration.between(now, cutoff);
        if (delay.isNegative()) {
            delay = Duration.ZERO;
        }

        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::recordPaymentId)
            .invoke(reservationId);

        log.info("Scheduling commitment-cutoff hold attempt for reservation {} in {}", reservationId, delay);
        timerScheduler.createSingleTimer(
            "commitment-cutoff-" + reservationId,
            delay,
            componentClient.forTimedAction()
                .method(CommitmentCutoffTimedAction::attemptHold)
                .deferred(new CommitmentCutoffTimedAction.HoldAttempt(reservationId, resourceId, slotStart, 1)));

        return effects().done();
    }
}
