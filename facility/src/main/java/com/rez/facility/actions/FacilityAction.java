package com.rez.facility.actions;

import com.rez.facility.entities.FacilityEntity;
import com.rez.facility.events.FacilityEvent;
import com.rez.facility.entities.ReservationEntity;
import com.rez.facility.entities.ResourceEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

@Subscribe.EventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Action {
    private final KalixClient kalixClient;

    public FacilityAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(FacilityEvent.ResourceSubmitted event) {
        var resourceEntityId = event.resourceId();
        var path = "/resource/%s/create".formatted(resourceEntityId);
        var command = new ResourceEntity.CreateResourceCommand(event.facilityId(), event.resource());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(FacilityEvent.ReservationCreated event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/init".formatted(reservationId);
        var command = new ReservationEntity.InitiateReservation(reservationId, event.facilityId(), event.reservation(), event.resources());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }
}
