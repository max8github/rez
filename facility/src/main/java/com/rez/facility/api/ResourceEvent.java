package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

public sealed interface ResourceEvent {

    @TypeName("resource-created")
    record ResourceCreated(String entityId, Dto.Resource resource, String facilityId) implements ResourceEvent {}
    record BookingAccepted(String reservationId, Dto.Reservation reservation, String resourceId) implements ResourceEvent {}
    record BookingRejected(String reservationId, Dto.Reservation reservation, String resourceId, String facilityId) implements ResourceEvent {}
}