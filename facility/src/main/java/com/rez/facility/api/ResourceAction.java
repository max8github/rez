package com.rez.facility.api;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

@Subscribe.EventSourcedEntity(value = ResourceEntity.class, ignoreUnknown = true)
public class ResourceAction extends Action {
    private final KalixClient kalixClient;

    public ResourceAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(ResourceEvent.ResourceCreated event) {
        var path = "/facility/%s/resource/%s".formatted(event.facilityId(), event.entityId());
        var deferredCall = kalixClient.post(path, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ResourceEvent.BookingAccepted event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/book".formatted(reservationId);
        var command = new ReservationEntity.Book(event.resourceId(), reservationId, event.reservation());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ResourceEvent.BookingRejected event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/runSearch".formatted(reservationId);
        var command = new ReservationEntity.RunSearch(reservationId, event.facilityId(), event.reservation());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ResourceEvent.BookingCanceled event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/cancel".formatted(reservationId);
        var deferredCall = kalixClient.delete(path, String.class);
        return effects().forward(deferredCall);
    }
}
