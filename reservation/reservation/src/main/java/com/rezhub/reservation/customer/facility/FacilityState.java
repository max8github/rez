package com.rezhub.reservation.customer.facility;

import java.util.Set;

public record FacilityState(String facilityId, String name, AddressState addressState,
                             String timezone, String botToken, Set<String> adminUserIds) {

  public static FacilityState create(String facilityId) {
    return new FacilityState(facilityId, "", new AddressState("", ""), null, null, null);
  }

  public FacilityState withName(String name) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds);
  }

  public FacilityState withAddressState(AddressState addressState) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds);
  }

  public FacilityState withTimezone(String timezone) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds);
  }

  public FacilityState withBotToken(String botToken) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds);
  }

  public FacilityState withAdminUserIds(Set<String> adminUserIds) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds);
  }
}
