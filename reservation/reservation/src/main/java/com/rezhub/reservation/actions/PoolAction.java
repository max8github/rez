package com.rezhub.reservation.actions;

import com.rezhub.reservation.pool.PoolEntity;
import com.rezhub.reservation.pool.PoolEvent;
import com.rezhub.reservation.resource.ResourceEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = PoolEntity.class, ignoreUnknown = true)
public class PoolAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(PoolAction.class);
    public static final int TIMEOUT = 5;
    private final ComponentClient kalixClient;

    public PoolAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    @SuppressWarnings("unused")
    public Effect<String> on(PoolEvent.ResourceSubmitted event) {
        var resourceEntityId = event.resourceId();
        var command = new ResourceEntity.CreateResourceCommand(event.poolId(), event.resourceDto());
        var deferredCall = kalixClient.forEventSourcedEntity(resourceEntityId).call(ResourceEntity::create).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(PoolEvent.AvalabilityRequested event) {
        log.info("fan out, continue the broadcast");
        CompletableFuture<Effect<String>> completableFuture = ReservationAction.futureBroadcast(this, kalixClient,
          event.reservationId(), event.reservation(), event.resources());

        return effects().asyncEffect(completableFuture);
    }

    static String timerName(String reservationId) {
        return "timer-" + reservationId;
    }
}
