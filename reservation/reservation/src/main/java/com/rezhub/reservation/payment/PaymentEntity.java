package com.rezhub.reservation.payment;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * The economic record for a single reservation's payment lifecycle. Entity id equals the owning
 * reservation's {@code reservationId} — see research.md #3 for why no separate id is generated.
 *
 * <p>Implements only the transitions this feature (Phase 1) drives: {@code NONE -> AUTHORIZED},
 * {@code AUTHORIZED -> CAPTURED}, and {@code {NONE, AUTHORIZED} -> FAILED}. Deliberately does not
 * implement {@code void()}/{@code refund()} — those are reserved for Phase 2 (rescue refund) and a
 * future admin/dispute-refund capability, per FR-017.
 */
@Component(id = "payment")
public class PaymentEntity extends EventSourcedEntity<PaymentState, PaymentEvent> {
    private static final Logger log = LoggerFactory.getLogger(PaymentEntity.class);

    private final String entityId;

    public PaymentEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public PaymentState emptyState() {
        return PaymentState.initiate(entityId);
    }

    @Override
    public PaymentState applyEvent(PaymentEvent event) {
        return switch (event) {
            case PaymentEvent.HoldAuthorized e -> currentState().withAuthorized(
                e.resourceId(), e.dateTime(), e.stripePaymentIntentId(), e.amountCents(), e.currency(),
                e.facilityConnectedAccountId(), e.applicationFeeCents());
            case PaymentEvent.HoldCaptured e -> currentState().withCaptured(e.stripeChargeId());
            case PaymentEvent.HoldCreationFailed e -> currentState().withFailed(e.resourceId(), e.dateTime(), e.reason());
        };
    }

    public record Authorize(String reservationId, String resourceId, LocalDateTime dateTime,
                            String stripePaymentIntentId, long amountCents, String currency,
                            String facilityConnectedAccountId, long applicationFeeCents) {}

    public Effect<String> authorize(Authorize command) {
        if (currentState().state() != PaymentState.State.NONE) {
            return effects().error("Payment " + entityId + " is not in NONE state — cannot authorize");
        }
        log.info("Payment {} authorized: PI={} amount={} {}", entityId, command.stripePaymentIntentId(),
            command.amountCents(), command.currency());
        return effects()
            .persist(new PaymentEvent.HoldAuthorized(entityId, command.reservationId(), command.resourceId(),
                command.dateTime(), command.stripePaymentIntentId(), command.amountCents(), command.currency(),
                command.facilityConnectedAccountId(), command.applicationFeeCents()))
            .thenReply(newState -> "OK");
    }

    public record Capture(String stripeChargeId) {}

    public Effect<String> capture(Capture command) {
        if (currentState().state() != PaymentState.State.AUTHORIZED) {
            return effects().error("Payment " + entityId + " is not in AUTHORIZED state — cannot capture");
        }
        log.info("Payment {} captured: charge={}", entityId, command.stripeChargeId());
        return effects()
            .persist(new PaymentEvent.HoldCaptured(entityId, command.stripeChargeId()))
            .thenReply(newState -> "OK");
    }

    public record Fail(String resourceId, LocalDateTime dateTime, String reason) {}

    public Effect<String> fail(Fail command) {
        PaymentState.State state = currentState().state();
        if (state != PaymentState.State.NONE && state != PaymentState.State.AUTHORIZED) {
            return effects().error("Payment " + entityId + " is in " + state + " — cannot fail");
        }
        log.info("Payment {} failed: {}", entityId, command.reason());
        return effects()
            .persist(new PaymentEvent.HoldCreationFailed(entityId, command.resourceId(), command.dateTime(), command.reason()))
            .thenReply(newState -> "OK");
    }

    public ReadOnlyEffect<PaymentState> getPayment() {
        return effects().reply(currentState());
    }
}
