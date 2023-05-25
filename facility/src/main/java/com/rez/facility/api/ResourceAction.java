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
}
