package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.customer.asset.dto.Asset;
import kalix.javasdk.annotations.TypeName;

public sealed interface FacilityEvent {

  @TypeName("created")
  record Created(String entityId, Facility facility) implements FacilityEvent {}

  @TypeName("renamed")
  record Renamed(String newName) implements FacilityEvent {}

  @TypeName("addressChanged")
  record AddressChanged(Address address) implements FacilityEvent {}

  @TypeName("asset-submitted")
  record AssetSubmitted(String facilityId, Asset asset, String assetId) implements FacilityEvent {}

  @TypeName("asset-id-added")
  record AssetIdAdded(String assetId) implements FacilityEvent {}

  @TypeName("asset-id-removed")
  record AssetIdRemoved(String assetId) implements FacilityEvent {}
}