package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

import java.util.List;

public sealed interface FacilityEvent {

    @TypeName("resource-submitted")
    record ResourceSubmitted(String facilityId, Dto.Resource resource, String resourceId) implements FacilityEvent {}

    @TypeName("resource-id-added")
    record ResourceIdAdded(String resourceEntityId) implements FacilityEvent {}

    @TypeName("resource-id-removed")
    record ResourceIdRemoved(String resourceEntityId) implements FacilityEvent {}

    @TypeName("created")
    record Created(String entityId, Dto.Facility facility) implements FacilityEvent {}

    @TypeName("renamed")
    record Renamed(String newName) implements FacilityEvent {}

    @TypeName("addressChanged")
    record AddressChanged(Dto.Address address) implements FacilityEvent {}

    @TypeName("reservation-created")
    record ReservationCreated(String reservationId, Dto.Reservation reservation, String facilityId,
                              List<String> resources) implements FacilityEvent {}

}