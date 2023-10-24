package com.rezhub.reservation.customer.asset;

import com.rezhub.reservation.customer.facility.FacilityEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = AssetEntity.class, ignoreUnknown = true)
public class AssetAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(AssetAction.class);
  private final ComponentClient kalixClient;

  public AssetAction(ComponentClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @SuppressWarnings("unused")
  public Effect<String> on(AssetEvent.FacilityAssetCreated event) {
    log.debug("Facility Asset was Created with id {}", event.assetId());
    var deferredCall = kalixClient.forEventSourcedEntity(event.facilityId()).call(FacilityEntity::registerAsset)
      .params(event.assetId());
    return effects().forward(deferredCall);
  }
}
