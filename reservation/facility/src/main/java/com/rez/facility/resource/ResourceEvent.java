package com.rez.facility.resource;

import com.rez.facility.dto.Reservation;
import com.rez.facility.resource.dto.Resource;
import kalix.javasdk.annotations.TypeName;

import java.time.LocalDateTime;

public sealed interface ResourceEvent {

    @TypeName("resource-created")
    record ResourceCreated(String entityId, Resource resourceDto, String facilityId) implements ResourceEvent {}
    @TypeName("booking-accepted")
    record BookingAccepted(String resourceId, String reservationId, String facilityId, Reservation reservationDto) implements ResourceEvent {}
    @TypeName("booking-rejected")
    record BookingRejected(String resourceId, String reservationId, String facilityId, Reservation reservationDto) implements ResourceEvent {}
    record BookingCanceled(String resourceId, String reservationId, LocalDateTime dateTime) implements ResourceEvent {}
}