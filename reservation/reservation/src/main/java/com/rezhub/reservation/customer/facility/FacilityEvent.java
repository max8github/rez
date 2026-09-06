package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.payment.PricingPolicy;
import akka.javasdk.annotations.TypeName;

public sealed interface FacilityEvent {

  @TypeName("facility-created")
  record Created(String entityId, Facility facility) implements FacilityEvent {}

  @TypeName("facility-renamed")
  record Renamed(String newName) implements FacilityEvent {}

  @TypeName("facility-address-changed")
  record AddressChanged(AddressState addressState) implements FacilityEvent {}

  @TypeName("facility-bot-token-updated")
  record BotTokenUpdated(String facilityId, String botToken, String timezone) implements FacilityEvent {}

  @TypeName("facility-pricing-policy-set")
  record PricingPolicySet(String facilityId, PricingPolicy policy) implements FacilityEvent {}

  @TypeName("facility-stripe-connected-account-set")
  record StripeConnectedAccountSet(String facilityId, String accountId) implements FacilityEvent {}
}
