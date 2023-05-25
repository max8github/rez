package com.rez.facility.api;

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
        var path = "/reservation/%s/kickoff".formatted(reservationId);
        var command = new KickoffBooking(reservationId, event.facilityId(), event.reservation());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.ResourceSelected event) {
        var resourceId = event.resourceId();
        var path = "/resource/%s/select".formatted(resourceId);
        var command = new SelectBooking(resourceId, event.reservationId(), event.facilityId(), event.reservation());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

//    public Effect<String> on(ReservationEvent.Booked event) {
//        var resourceId = event.resourceId();
//        var path = "/calendar/save";
//        var command = new SelectBooking(resourceId, event.reservation(), event.reservationId());
//        var deferredCall = kalixClient.post(path, command, String.class);
//        return effects().forward(deferredCall);
//    }

    public Effect<String> on(ResourceEvent.BookingAccepted event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/book".formatted(reservationId);
        var command = new Book(event.resourceId(), reservationId, event.reservation());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ResourceEvent.BookingRejected event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/reject".formatted(reservationId);
        var command = new Reject(event.resourceId(), reservationId, event.facilityId(), event.reservation());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public record KickoffBooking(String reservationId, String facilityId, Api.Reservation reservation) {}
    public record SelectBooking(String resourceId, String reservationId, String facilityId, Api.Reservation reservation) {}

    public record Book(String resourceId, String reservationId, Api.Reservation reservation) {}
    public record Reject(String resourceId, String reservationId, String facilityId, Api.Reservation reservation) {}
}
