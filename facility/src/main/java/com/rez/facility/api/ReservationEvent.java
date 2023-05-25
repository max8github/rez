package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

import java.util.List;

public sealed interface ReservationEvent {
    @TypeName("reservation-initiated")
    record ReservationInitiated(String reservationId, Dto.Reservation reservation, String facilityId,
                                List<String> resources) implements ReservationEvent {}
    @TypeName("reservation-rejected")
    record ReservationRejected(String reservationId, Dto.Reservation reservation, String facilityId) implements ReservationEvent {}

    @TypeName("reservation-selected")
    record ResourceSelected(String resourceId, Dto.Reservation reservation, String reservationId, String facilityId) implements ReservationEvent {}
    @TypeName("booked")
    record Booked(String resourceId, Dto.Reservation reservation, String reservationId) implements ReservationEvent {}
}