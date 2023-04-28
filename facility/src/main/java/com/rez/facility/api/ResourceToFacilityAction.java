package com.rez.facility.api;

import com.rez.facility.domain.ResourceEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

@Subscribe.EventSourcedEntity(value = ResourceEntity.class, ignoreUnknown = true)
public class ResourceToFacilityAction extends Action {
    private final KalixClient kalixClient;

    public ResourceToFacilityAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(ResourceEvent.ResourceCreated event) {
        var path = "/facility/%s/resource/%s".formatted(event.facilityId(), event.entityId());
        var deferredCall = kalixClient.post(path, String.class);
        return effects().forward(deferredCall);
    }
}
