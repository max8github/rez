package com.rez.facility.pool;

import com.rez.facility.dto.*;
import com.rez.facility.pool.dto.Address;
import com.rez.facility.pool.dto.Facility;
import com.rez.facility.resource.dto.Resource;
import kalix.javasdk.annotations.TypeName;

import java.util.List;

public sealed interface FacilityEvent {

    @TypeName("created")
    record Created(String entityId, Facility facility) implements FacilityEvent {}

    @TypeName("renamed")
    record Renamed(String newName) implements FacilityEvent {}

    @TypeName("addressChanged")
    record AddressChanged(Address address) implements FacilityEvent {}

    @TypeName("resource-submitted")
    record ResourceSubmitted(String facilityId, Resource resourceDto, String resourceId) implements FacilityEvent {}

    @TypeName("resource-id-added")
    record ResourceIdAdded(String resourceEntityId) implements FacilityEvent {}

    @TypeName("resource-id-removed")
    record ResourceIdRemoved(String resourceEntityId) implements FacilityEvent {}

    @TypeName("reservation-created")
    record ReservationCreated(String reservationId, String facilityId, Reservation reservationDto,
                              List<String> resources) implements FacilityEvent {}
}