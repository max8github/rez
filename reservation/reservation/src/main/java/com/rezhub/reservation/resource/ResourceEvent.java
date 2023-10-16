package com.rezhub.reservation.resource;

import com.rezhub.reservation.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.time.LocalDateTime;

public sealed interface ResourceEvent {

    @TypeName("resource-created")
    record ResourceCreated(String resourceId, String resourceName, String facilityId) implements ResourceEvent {}
    @TypeName("availability-checked")
    record AvalabilityChecked(String resourceId, String reservationId, boolean available, String facilityId) implements ResourceEvent {}
    @TypeName("reservation-accepted")
    record ReservationAccepted(String resourceId, String reservationId, String facilityId, Reservation reservationDto) implements ResourceEvent {}
    @TypeName("reservation-rejected")
    record ReservationRejected(String resourceId, String reservationId, String facilityId, Reservation reservation) implements ResourceEvent {}
    record ReservationCanceled(String resourceId, String reservationId, LocalDateTime dateTime) implements ResourceEvent {}
}