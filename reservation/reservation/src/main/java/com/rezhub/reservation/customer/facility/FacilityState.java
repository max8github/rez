package com.rezhub.reservation.customer.facility;

import java.util.HashSet;
import java.util.Set;

public record FacilityState(String facilityId, String name, AddressState addressState, Set<String> resourceIds,
                             String timezone, String botToken, Set<String> adminUserIds) {

  public static FacilityState create(String facilityId) {
    return new FacilityState(facilityId, "", new AddressState("", ""), new HashSet<>(), null, null, null);
  }

  public FacilityState withName(String name) {
    return new FacilityState(facilityId, name, addressState, resourceIds, timezone, botToken, adminUserIds);
  }

  public FacilityState withAddressState(AddressState addressState) {
    return new FacilityState(facilityId, name, addressState, resourceIds, timezone, botToken, adminUserIds);
  }

  public FacilityState withResourceIds(Set<String> resourceIds) {
    return new FacilityState(facilityId, name, addressState, normalizeResourceIds(resourceIds), timezone, botToken, adminUserIds);
  }

  public FacilityState withTimezone(String timezone) {
    return new FacilityState(facilityId, name, addressState, resourceIds, timezone, botToken, adminUserIds);
  }

  public FacilityState withBotToken(String botToken) {
    return new FacilityState(facilityId, name, addressState, resourceIds, timezone, botToken, adminUserIds);
  }

  public FacilityState withAdminUserIds(Set<String> adminUserIds) {
    return new FacilityState(facilityId, name, addressState, resourceIds, timezone, botToken, adminUserIds);
  }

  public FacilityState registerResource(String resourceId) {
    Set<String> ids = new HashSet<>(normalizeResourceIds(resourceIds));
    ids.add(resourceId);
    return new FacilityState(facilityId, name, addressState, ids, timezone, botToken, adminUserIds);
  }

  public FacilityState unregisterResource(String resourceId) {
    Set<String> ids = new HashSet<>(normalizeResourceIds(resourceIds));
    ids.remove(resourceId);
    return new FacilityState(facilityId, name, addressState, ids, timezone, botToken, adminUserIds);
  }

  public static Set<String> normalizeResourceIds(Set<String> resourceIds) {
    return resourceIds == null ? Set.of() : Set.copyOf(resourceIds);
  }
}
