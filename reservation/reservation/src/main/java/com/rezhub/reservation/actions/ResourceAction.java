package com.rezhub.reservation.actions;

import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = ResourceEntity.class, ignoreUnknown = true)
public class ResourceAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(ResourceAction.class);

    private final ComponentClient kalixClient;

    public ResourceAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.FacilityResourceCreated event) {
        log.debug("Facility Asset was Created with id {}", event.resourceId());
        var deferredCall = kalixClient.forEventSourcedEntity(event.parentId()).call(FacilityEntity::registerResource)
          .params(event.resourceId());
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.AvalabilityChecked event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.ReplyAvailability(reservationId, event.resourceId(), event.available());
        var deferredCall = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::replyAvailability).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ReservationAccepted event) {
        var resourceId = event.resourceId();
        String reservationId = event.reservationId();
        log.info("Resource {} sends acceptance to reservation {}", resourceId, reservationId);
        var command = new ReservationEntity.Fulfill(event.resourceId(), reservationId, event.reservation());

        CompletionStage<String> reply = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::fulfill).params(command)
          .execute()
          .thenCompose(req -> timers().cancel(RezAction.timerName(reservationId)))
          .thenApply(done -> "Ok");

        return effects().asyncReply(reply);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ReservationRejected event) {
        log.info("Resource {} sends rejection to reservation {}, tryNext", event.resourceId(), event.reservationId());
        var reservationId = event.reservationId();
        var command = new ReservationEntity.Reject(event.resourceId());
        var deferredCall = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::reject).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ReservationCanceled event) {
        var reservationId = event.reservationId();
        var deferredCall = kalixClient.forEventSourcedEntity(reservationId)
                .call(ReservationEntity::cancel);
        return effects().forward(deferredCall);
    }
}
