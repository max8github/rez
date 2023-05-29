package com.rez.facility.api;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class DelegatingServiceAction extends Action {
    private final KalixClient kalixClient;

    public DelegatingServiceAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(ReservationEvent.Booked event) {
        var resourceId = event.resourceId();
        var command = new ResourceEntity.InquireBooking(resourceId, event.reservationId(), "facilityId", event.reservation());
        var path = "/calendar/save";
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }
}