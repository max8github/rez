package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

public sealed interface ResourceEvent {

    @TypeName("resource-created")
    record ResourceCreated(String entityId, Api.Resource resource, String facilityId) implements ResourceEvent {}
    @TypeName("booking-accepted")
    record BookingAccepted(String resourceId, String reservationId, String facilityId, Api.Reservation reservation) implements ResourceEvent {}
    @TypeName("booking-rejected")
    record BookingRejected(String resourceId, String reservationId, String facilityId, Api.Reservation reservation) implements ResourceEvent {}
}