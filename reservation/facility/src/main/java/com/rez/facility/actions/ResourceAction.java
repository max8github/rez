package com.rez.facility.actions;

import com.rez.facility.pool.FacilityEntity;
import com.rez.facility.reservation.ReservationEntity;
import com.rez.facility.resource.ResourceEntity;
import com.rez.facility.resource.ResourceEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = ResourceEntity.class, ignoreUnknown = true)
public class ResourceAction extends Action {
    private final ComponentClient kalixClient;

    public ResourceAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ResourceCreated event) {
        var deferredCall = kalixClient.forEventSourcedEntity(event.facilityId())
                .call(FacilityEntity::addResourceId).params(event.entityId());
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.BookingAccepted event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.Book(event.resourceId(), reservationId, event.reservationDto(), event.facilityId());
        var deferredCall = kalixClient.forWorkflow(reservationId).call(ReservationEntity::book).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.BookingRejected event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.RunSearch(reservationId, event.facilityId(), event.reservationDto());
        var deferredCall = kalixClient.forWorkflow(reservationId).call(ReservationEntity::runSearch).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.BookingCanceled event) {
        var reservationId = event.reservationId();
        var deferredCall = kalixClient.forWorkflow(reservationId)
                .call(ReservationEntity::cancel);
        return effects().forward(deferredCall);
    }
}
