package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.asset.AssetEntity;
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
  public Effect<String> on(FacilityEvent.AssetCreateAndRegisterRequested event) {
    var assetEntityId = event.assetId();
    var command = new AssetEntity.CreateFacilityAsset(event.facilityId(), event.asset());
    var deferredCall = kalixClient.forEventSourcedEntity(assetEntityId).call(AssetEntity::createFacilityAsset).params(command);
    return effects().forward(deferredCall);
  }
}
