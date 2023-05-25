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
        var resId = event.reservationId();
        var path = "/reservation/%s/kickoff".formatted(resId);
        var command = new KickoffBooking(resId, event.facilityId(), event.reservationDTO());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.ResourceSelected event) {
        var resourceId = event.resourceId();
        var path = "/resource/%s/select".formatted(resourceId);
        var command = new SelectBooking(resourceId, event.reservationDTO(), event.reservationId(), event.facilityId());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

//    public Effect<String> on(ReservationEvent.Booked event) {
//        var resourceId = event.resourceId();
//        var path = "/calendar/save";
//        var command = new SelectBooking(resourceId, event.reservationDTO(), event.reservationId());
//        var deferredCall = kalixClient.post(path, command, String.class);
//        return effects().forward(deferredCall);
//    }

    public Effect<String> on(ResourceEvent.BookingAccepted event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/book".formatted(reservationId);
        var command = new Book(event.resourceId(), event.reservationDTO(), reservationId);
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ResourceEvent.BookingRejected event) {
        var reservationId = event.reservationId();
        var path = "/reservation/%s/reject".formatted(reservationId);
        var command = new Reject(event.resourceId(), event.reservationDTO(), reservationId, event.facilityId());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public record KickoffBooking(String reservationId, String facilityId, Dto.ReservationDTO reservationDTO) {}
    public record SelectBooking(String resourceId, Dto.ReservationDTO reservationDTO, String reservationId, String facilityId) {}

    public record Book(String resourceId, Dto.ReservationDTO reservationDTO, String reservationId) {}
    public record Reject(String resourceId, Dto.ReservationDTO reservationDTO, String reservationId, String facilityId) {}
}
