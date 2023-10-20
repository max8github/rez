package com.rezhub.reservation.pool;

import com.rezhub.reservation.pool.dto.Facility;
import com.rezhub.reservation.resource.dto.Resource;
import com.rezhub.reservation.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.util.Set;

public sealed interface FacilityEvent {

    @TypeName("created")
    record Created(String entityId, Facility facility) implements FacilityEvent {}

    @TypeName("renamed")
    record Renamed(String newName) implements FacilityEvent {}

    @TypeName("resource-submitted")
    record ResourceSubmitted(String facilityId, Resource resourceDto, String resourceId) implements FacilityEvent {}

    @TypeName("resource-id-added")
    record ResourceIdAdded(String resourceEntityId) implements FacilityEvent {}

    @TypeName("resource-id-removed")
    record ResourceIdRemoved(String resourceEntityId) implements FacilityEvent {}

    @TypeName("reservation-created")
    record ReservationCreated(String reservationId, String facilityId, Reservation reservationDto,
                              Set<String> resources) implements FacilityEvent {}
}