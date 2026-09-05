package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.payment.PricingPolicy;

import java.util.Optional;
import java.util.Set;

public record FacilityState(String facilityId, String name, AddressState addressState,
                             String timezone, String botToken, Set<String> adminUserIds,
                             Optional<PricingPolicy> pricingPolicy, Optional<String> stripeConnectedAccountId) {

  public static FacilityState create(String facilityId) {
    return new FacilityState(facilityId, "", new AddressState("", ""), null, null, null,
        Optional.empty(), Optional.empty());
  }

  public FacilityState withName(String name) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds,
        pricingPolicy, stripeConnectedAccountId);
  }

  public FacilityState withAddressState(AddressState addressState) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds,
        pricingPolicy, stripeConnectedAccountId);
  }

  public FacilityState withTimezone(String timezone) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds,
        pricingPolicy, stripeConnectedAccountId);
  }

  public FacilityState withBotToken(String botToken) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds,
        pricingPolicy, stripeConnectedAccountId);
  }

  public FacilityState withAdminUserIds(Set<String> adminUserIds) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds,
        pricingPolicy, stripeConnectedAccountId);
  }

  public FacilityState withPricingPolicy(PricingPolicy pricingPolicy) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds,
        Optional.of(pricingPolicy), stripeConnectedAccountId);
  }

  public FacilityState withStripeConnectedAccountId(String stripeConnectedAccountId) {
    return new FacilityState(facilityId, name, addressState, timezone, botToken, adminUserIds,
        pricingPolicy, Optional.of(stripeConnectedAccountId));
  }
}
