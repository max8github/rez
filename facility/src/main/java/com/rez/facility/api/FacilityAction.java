package com.rez.facility.api;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

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

    public record CreateResourceCommand(String facilityId, Dto.ResourceDTO resourceDTO) {}
}
