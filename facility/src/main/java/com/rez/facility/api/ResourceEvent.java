package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

public sealed interface ResourceEvent {

    @TypeName("resource-created")
    record ResourceCreated(String entityId, Api.Resource resource, String facilityId) implements ResourceEvent {}
    record BookingAccepted(String reservationId, Api.Reservation reservation, String resourceId) implements ResourceEvent {}
    record BookingRejected(String reservationId, Api.Reservation reservation, String resourceId, String facilityId) implements ResourceEvent {}
}