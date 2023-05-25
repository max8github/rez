package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

import java.util.List;

public sealed interface ReservationEvent {
    @TypeName("reservation-initiated")
    record ReservationInitiated(String reservationId, String facilityId, Api.Reservation reservation,
                                List<String> resources) implements ReservationEvent {}
    @TypeName("reservation-rejected")
    record ReservationRejected(String reservationId, String facilityId, Api.Reservation reservation) implements ReservationEvent {}

    @TypeName("reservation-selected")
    record ResourceSelected(String resourceId, String reservationId, String facilityId, Api.Reservation reservation) implements ReservationEvent {}
    @TypeName("booked")
    record Booked(String resourceId, String reservationId, Api.Reservation reservation) implements ReservationEvent {}
}