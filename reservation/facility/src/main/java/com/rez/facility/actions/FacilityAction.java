package com.rez.facility.actions;

import com.rez.facility.entities.FacilityEntity;
import com.rez.facility.events.FacilityEvent;
import com.rez.facility.entities.ReservationEntity;
import com.rez.facility.entities.ResourceEntity;
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
        var command = new ResourceEntity.CreateResourceCommand(event.facilityId(), event.resource());
        var deferredCall = kalixClient.forEventSourcedEntity(resourceEntityId).call(ResourceEntity::create).params(command);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(FacilityEvent.ReservationCreated event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.InitiateReservation(reservationId, event.facilityId(), event.reservation(), event.resources());
        var deferredCall = kalixClient.forWorkflow(reservationId).call(ReservationEntity::init).params(command, reservationId);
        return effects().forward(deferredCall);
    }
}
