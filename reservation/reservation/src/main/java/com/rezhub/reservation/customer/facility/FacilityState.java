package com.rezhub.reservation.customer.facility;

import java.util.HashSet;
import java.util.Set;

public record FacilityState(String facilityId, String name, AddressState addressState, Set<String> resourceIds) {

  public static FacilityState create(String facilityId) {
    return new FacilityState(facilityId, "", new AddressState("", ""), new HashSet<>());
  }

  public FacilityState withName(String name) {
    return new FacilityState(facilityId, name, addressState, resourceIds);
  }

  public FacilityState withAddressState(AddressState addressState) {
    return new FacilityState(facilityId, name, addressState, resourceIds);
  }

  public FacilityState withResourceIds(Set<String> resourceIds) {
    return new FacilityState(facilityId, name, addressState, resourceIds);
  }

  public FacilityState registerResource(String resourceId) {
    Set<String> ids = (resourceIds == null) ? new HashSet<>() : new HashSet<>(resourceIds);
    ids.add(resourceId);
    return new FacilityState(facilityId, name, addressState, ids);
  }

  public FacilityState unregisterResource(String resourceId) {
    Set<String> ids = new HashSet<>(resourceIds);
    ids.remove(resourceId);
    return new FacilityState(facilityId, name, addressState, ids);
  }
}
