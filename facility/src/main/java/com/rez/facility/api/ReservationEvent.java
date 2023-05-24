package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

import java.util.List;

public sealed interface ReservationEvent {
    @TypeName("reservation-initiated")
    record ReservationInitiated(String reservationId, Dto.ReservationDTO reservationDTO, String facilityId,
                               List<String> resources) implements ReservationEvent {}
    @TypeName("reservation-rejected")
    record ReservationRejected(String reservationId, Dto.ReservationDTO reservationDTO, String facilityId) implements ReservationEvent {}

    @TypeName("reservation-selected")
    record ResourceSelected(String resourceId, Dto.ReservationDTO reservationDTO, String reservationId) implements ReservationEvent {}
    @TypeName("booked")
    record Booked(String resourceId, Dto.ReservationDTO reservationDTO, String reservationId) implements ReservationEvent {}
    @TypeName("rejected")
    record Rejected(String reservationId) implements ReservationEvent {}
}