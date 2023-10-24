package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.customer.asset.dto.Asset;
import kalix.javasdk.annotations.TypeName;

public sealed interface FacilityEvent {

  @TypeName("facility-created")
  record Created(String entityId, Facility facility) implements FacilityEvent {}

  @TypeName("facility-renamed")
  record Renamed(String newName) implements FacilityEvent {}

  @TypeName("address-changed")
  record AddressChanged(AddressState addressState) implements FacilityEvent {}

  @TypeName("asset-create-register-requested")
  record AssetCreateAndRegisterRequested(String facilityId, Asset asset, String assetId) implements FacilityEvent {}

  @TypeName("asset-registered")
  record AssetRegistered(String assetId) implements FacilityEvent {}

  @TypeName("asset-id-removed")
  record AssetIdRemoved(String assetId) implements FacilityEvent {}
}