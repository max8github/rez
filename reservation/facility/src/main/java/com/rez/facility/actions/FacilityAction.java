package com.rez.facility.actions;

import com.rez.facility.pool.FacilityEntity;
import com.rez.facility.pool.FacilityEvent;
import com.rez.facility.reservation.ReservationEntity;
import com.rez.facility.resource.ResourceEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Action {
    private final ComponentClient kalixClient;

    public FacilityAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(FacilityEvent.ResourceSubmitted event) {
        var resourceEntityId = event.resourceId();
        var command = new ResourceEntity.CreateResourceCommand(event.facilityId(), event.resourceDto());
        var deferredCall = kalixClient.forEventSourcedEntity(resourceEntityId).call(ResourceEntity::create).params(command);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(FacilityEvent.ReservationCreated event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.InitiateReservation(reservationId, event.facilityId(), event.reservationDto(), event.resources());
        var deferredCall = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::init).params(command);
        return effects().forward(deferredCall);
    }
}
