package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

public sealed interface FacilityEvent {

    @TypeName("resource-submitted")
    record ResourceSubmitted(String facilityId, Dto.ResourceDTO resourceDTO) implements FacilityEvent {}

    @TypeName("resource-id-added")
    record ResourceIdAdded(String resourceEntityId) implements FacilityEvent {}

    @TypeName("resource-id-removed")
    record ResourceIdRemoved(String resourceEntityId) implements FacilityEvent {}

    @TypeName("created")
    record Created(String entityId, Dto.FacilityDTO facilityDTO) implements FacilityEvent {}

    @TypeName("renamed")
    record Renamed(String newName) implements FacilityEvent {}

    @TypeName("addressChanged")
    record AddressChanged(Dto.AddressDTO addressDTO) implements FacilityEvent {}
}