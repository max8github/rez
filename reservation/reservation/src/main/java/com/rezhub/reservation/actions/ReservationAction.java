package com.rezhub.reservation.actions;

import com.google.protobuf.any.Any;
import com.rezhub.reservation.customer.facility.FacilityAction;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationEvent;
import com.rezhub.reservation.resource.ResourceEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class ReservationAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(ReservationAction.class);
    private final ComponentClient kalixClient;

    public ReservationAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(ReservationEvent.Inited event) {
        log.info("Broadcast starts to selection {}", event.selection());
        CompletableFuture<Effect<String>> completableFuture = FacilityAction.broadcast(this, kalixClient, event.reservationId(),
          event.reservation(), event.selection());
        return effects().asyncEffect(completableFuture);
    }

    public Effect<String> on(ReservationEvent.ResourceSelected event) {
        log.info("Reservation {} has a candidate ({}) and sends a Fulfill to it", event.reservationId(), event.resourceId());
        var resourceId = event.resourceId();
        var command = new ResourceEntity.Reserve(event.reservationId(), event.reservation());
        DeferredCall<Any, String> deferredCall = kalixClient.forEventSourcedEntity(resourceId).call(ResourceEntity::reserve).params(command);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.RejectedWithNext event) {
        var reservationId = event.reservationId();
        var resourceId = event.resourceId();
        var nextResourceId = event.nextResourceId();
        log.info("Reservation {} had a candidate ({}), but that got subsequently rejected. Now to try: {}.",
          reservationId, resourceId, nextResourceId);
        var command = new ReservationEntity.ReplyAvailability(reservationId, event.nextResourceId(), true);
        DeferredCall<Any, String> deferredCall = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::replyAvailability).params(command);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.CancelRequested event) {
        log.info("Cancel reservation {} in resource {}", event.reservationId(), event.resourceId());
        var resourceId = event.resourceId();
        var deferredCall = kalixClient.forEventSourcedEntity(resourceId).call(ResourceEntity::cancel)
                .params(event.reservationId(), event.dateTime().toString());
        return effects().forward(deferredCall);
    }
}
