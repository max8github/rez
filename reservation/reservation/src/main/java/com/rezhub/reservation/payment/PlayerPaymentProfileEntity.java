package com.rezhub.reservation.payment;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps a player's canonical {@code identity} {@code userId} (entity id) to a Stripe
 * {@code customerId} and default {@code paymentMethodId}. Populated by {@code StripeWebhookEndpoint}
 * on {@code setup_intent.succeeded} / {@code payment_method.attached}.
 */
@Component(id = "player-payment-profile")
public class PlayerPaymentProfileEntity extends KeyValueEntity<PlayerPaymentProfileState> {
    private static final Logger log = LoggerFactory.getLogger(PlayerPaymentProfileEntity.class);

    private final String entityId;

    public PlayerPaymentProfileEntity(KeyValueEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public PlayerPaymentProfileState emptyState() {
        return PlayerPaymentProfileState.empty(entityId);
    }

    public Effect<Done> linkCustomer(String stripeCustomerId) {
        log.info("Linking Stripe customer {} to player {}", stripeCustomerId, entityId);
        return effects()
            .updateState(currentState().withStripeCustomerId(stripeCustomerId))
            .thenReply(Done.getInstance());
    }

    public Effect<Done> setDefaultPaymentMethod(String paymentMethodId) {
        log.info("Setting default payment method for player {}", entityId);
        return effects()
            .updateState(currentState().withDefaultPaymentMethodId(paymentMethodId))
            .thenReply(Done.getInstance());
    }

    public ReadOnlyEffect<PlayerPaymentProfileState> getProfile() {
        return effects().reply(currentState());
    }
}
