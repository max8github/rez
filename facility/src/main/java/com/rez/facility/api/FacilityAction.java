package com.rez.facility.api;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

import java.util.List;

@Subscribe.EventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Action {
    private final KalixClient kalixClient;

    public FacilityAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(FacilityEvent.ResourceSubmitted event) {
        var resourceEntityId = event.resourceId();
        var path = "/resource/%s/create".formatted(resourceEntityId);
        var command = new CreateResourceCommand(event.facilityId(), event.resource());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(FacilityEvent.ReservationCreated event) {
        var resId = event.reservationId();
        var path = "/reservation/%s/init".formatted(resId);
        var command = new InitiateReservation(resId, event.facilityId(), event.reservation(), event.resources());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public record CreateResourceCommand(String facilityId, Api.Resource resource) {}
    public record InitiateReservation(String reservationId, String facilityId, Api.Reservation reservation,
                                      List<String> resources) {}}
