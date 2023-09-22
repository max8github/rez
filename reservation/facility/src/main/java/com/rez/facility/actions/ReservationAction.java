package com.rez.facility.actions;

import com.rez.facility.entities.ReservationEntity;
import com.rez.facility.events.ReservationEvent;
import com.rez.facility.entities.ResourceEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class ReservationAction extends Action {
    private final KalixClient kalixClient;

    public ReservationAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(ReservationEvent.ReservationInitiated event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/runSearch".formatted(reservationId);
        var command = new ReservationEntity.RunSearch(reservationId, event.facilityId(), event.reservation());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.ResourceSelected event) {
        var resourceId = event.resourceId();
        var path = "/resource/%s/inquireBooking".formatted(resourceId);
        var command = new ResourceEntity.InquireBooking(resourceId, event.reservationId(), event.facilityId(), event.reservation());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.CancelRequested event) {
        var resourceId = event.resourceId();
        var path = "/resource/%s/reservation/%s/%s".formatted(resourceId, event.reservationId(), event.dateTime().toString());
        var deferredCall = kalixClient.delete(path, String.class);
        return effects().forward(deferredCall);
    }
}
