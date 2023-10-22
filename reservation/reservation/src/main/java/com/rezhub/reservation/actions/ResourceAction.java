package com.rezhub.reservation.actions;

import com.rezhub.reservation.pool.FacilityEntity;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = ResourceEntity.class, ignoreUnknown = true)
public class ResourceAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(ResourceAction.class);

    private final ComponentClient kalixClient;

    public ResourceAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ResourceCreated event) {
        var deferredCall = kalixClient.forEventSourcedEntity(event.facilityId())
          .call(FacilityEntity::addResourceId).params(event.resourceId());
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ReservationAccepted event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.Book(event.resourceId(), reservationId, event.reservation(), event.facilityId());
        var deferredCall = kalixClient.forWorkflow(reservationId).call(ReservationEntity::book).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ReservationRejected event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.RunSearch(reservationId, event.facilityId(), event.reservation());
        var deferredCall = kalixClient.forWorkflow(reservationId).call(ReservationEntity::runSearch).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ReservationCanceled event) {
        var reservationId = event.reservationId();
        var deferredCall = kalixClient.forWorkflow(reservationId)
          .call(ReservationEntity::cancel);
        return effects().forward(deferredCall);
    }
}
