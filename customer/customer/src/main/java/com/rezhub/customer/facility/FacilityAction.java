package com.rezhub.customer.facility;

import com.rezhub.customer.resource.ResourceEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Action {
  private final ComponentClient kalixClient;

  public FacilityAction(ComponentClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @SuppressWarnings("unused")
  public Effect<String> on(FacilityEvent.ResourceSubmitted event) {
    var resourceEntityId = event.resourceId();
    var command = new ResourceEntity.CreateResourceCommand(event.facilityId(), event.resource());
    var deferredCall = kalixClient.forEventSourcedEntity(resourceEntityId).call(ResourceEntity::create).params(command);
    return effects().forward(deferredCall);
  }
}
