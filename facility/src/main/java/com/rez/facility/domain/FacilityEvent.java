package com.rez.facility.domain;

import com.rez.facility.api.Dto;
import kalix.javasdk.annotations.TypeName;

public sealed interface FacilityEvent {

    @TypeName("resource-added")
    record ResourceAdded(Dto.ResourceDTO resource) implements FacilityEvent {}

    @TypeName("resource-removed")
    record ResourceRemoved(String resourceId) implements FacilityEvent {}

    @TypeName("created")
    record Created(String entityId, Dto.FacilityDTO facilityDTO) implements FacilityEvent {}
}
// end::events[]