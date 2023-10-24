package com.rezhub.reservation.customer.asset;

import kalix.javasdk.annotations.TypeName;

public sealed interface AssetEvent {

  @TypeName("asset-created")
  record AssetCreated(String assetId, String assetName) implements AssetEvent {}
  @TypeName("facility-asset-created")
  record FacilityAssetCreated(String assetId, String assetName, String facilityId) implements AssetEvent {}
}