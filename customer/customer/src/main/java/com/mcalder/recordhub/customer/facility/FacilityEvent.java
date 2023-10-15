package com.mcalder.recordhub.customer.facility;

import com.mcalder.recordhub.customer.facility.dto.Address;
import com.mcalder.recordhub.customer.facility.dto.Facility;
import com.mcalder.recordhub.customer.resource.dto.Resource;
import kalix.javasdk.annotations.TypeName;

public sealed interface FacilityEvent {

  @TypeName("created")
  record Created(String entityId, Facility facility) implements FacilityEvent {}

  @TypeName("renamed")
  record Renamed(String newName) implements FacilityEvent {}

  @TypeName("addressChanged")
  record AddressChanged(Address address) implements FacilityEvent {}

  @TypeName("resource-submitted")
  record ResourceSubmitted(String facilityId, Resource resource, String resourceId) implements FacilityEvent {}

  @TypeName("resource-id-added")
  record ResourceIdAdded(String resourceId) implements FacilityEvent {}

  @TypeName("resource-id-removed")
  record ResourceIdRemoved(String resourceId) implements FacilityEvent {}
}