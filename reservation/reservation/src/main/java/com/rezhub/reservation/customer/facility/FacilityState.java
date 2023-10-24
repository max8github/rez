package com.rezhub.reservation.customer.facility;

import lombok.With;

import java.util.HashSet;
import java.util.Set;

@With
public record FacilityState(String facilityId, String name, AddressState addressState, Set<String> assetIds) {

  public static FacilityState create(String facilityId) {
    return new FacilityState(facilityId, "", new AddressState("", ""), new HashSet<>());
  }

  public FacilityState registerAsset(String assetId) {
    Set<String> ids = (assetIds == null) ? new HashSet<>() : new HashSet<>(assetIds);
    ids.add(assetId);
    return new FacilityState(facilityId, name, addressState, ids);
  }

  public FacilityState unregisterAsset(String assetId) {
    Set<String> ids = new HashSet<>(assetIds);
    ids.remove(assetId);
    return new FacilityState(facilityId, name, addressState, ids);
  }
}
