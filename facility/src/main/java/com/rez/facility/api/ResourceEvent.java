package com.rez.facility.api;

import com.rez.facility.dto.Reservation;
import com.rez.facility.dto.Resource;
import kalix.javasdk.annotations.TypeName;

public sealed interface ResourceEvent {

    @TypeName("resource-created")
    record ResourceCreated(String entityId, Resource resource, String facilityId) implements ResourceEvent {}
    @TypeName("booking-accepted")
    record BookingAccepted(String resourceId, String reservationId, String facilityId, Reservation reservation) implements ResourceEvent {}
    @TypeName("booking-rejected")
    record BookingRejected(String resourceId, String reservationId, String facilityId, Reservation reservation) implements ResourceEvent {}
    record BookingCanceled(String resourceId, String reservationId, int timeSlot) implements ResourceEvent {}
}