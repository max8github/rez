package com.rez.facility.api;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

import java.util.List;
import java.util.UUID;

@Subscribe.EventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Action {
    private final KalixClient kalixClient;

    public FacilityAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(FacilityEvent.ResourceSubmitted event) {
        var resourceEntityId = UUID.randomUUID().toString();
        var path = "/resource/%s/create".formatted(resourceEntityId);
        var command = new CreateResourceCommand(event.facilityId(), event.resourceDTO());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(FacilityEvent.ReservationCreated event) {
        var resId = event.reservationId();
        var path = "/reservation/%s/init".formatted(resId);
        var command = new InitiateReservation(resId, event.facilityId(), event.reservationDTO(), event.resources());
        var deferredCall = kalixClient.post(path, command, String.class);
        return effects().forward(deferredCall);
    }

    public record CreateResourceCommand(String facilityId, Dto.ResourceDTO resourceDTO) {}
    public record InitiateReservation(String reservationId, String facilityId, Dto.ReservationDTO reservationDTO,
                                      List<String> resources) {}}
