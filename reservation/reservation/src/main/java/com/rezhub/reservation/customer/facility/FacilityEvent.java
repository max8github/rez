package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.util.Set;

public sealed interface FacilityEvent {

  @TypeName("facility-created")
  record Created(String entityId, Facility facility) implements FacilityEvent {}

  @TypeName("facility-renamed")
  record Renamed(String newName) implements FacilityEvent {}

  @TypeName("facility-address-changed")
  record AddressChanged(AddressState addressState) implements FacilityEvent {}

  @TypeName("resource-create-register-requested")
  record ResourceCreateAndRegisterRequested(String facilityId, String resourceName, String resourceId) implements FacilityEvent {}

  @TypeName("resource-registered")
  record ResourceRegistered(String resourceId) implements FacilityEvent {}

  @TypeName("resource-id-removed")
  record ResourceUnregistered(String resourceId) implements FacilityEvent {}

  @TypeName("facility-availability-requested")
  record AvalabilityRequested(String reservationId, Reservation reservation, Set<String> resources) implements FacilityEvent {}
}