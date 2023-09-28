package com.rez.facility.reservation;

import com.rez.facility.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.time.LocalDateTime;
import java.util.List;

public sealed interface ReservationEvent {
    @TypeName("reservation-initiated")
    record ReservationInitiated(String reservationId, String facilityId, Reservation reservationDto,
                                List<String> resources) implements ReservationEvent {}
    @TypeName("reservation-cancelled")
    record ReservationCancelled(String reservationId, String facilityId, Reservation reservationDto, String resourceId,
                                List<String> resourceIds) implements ReservationEvent {}
    @TypeName("cancel-requested")
    record CancelRequested(String reservationId, String facilityId, String resourceId, LocalDateTime dateTime) implements ReservationEvent {}

    @TypeName("search-exhausted")
    record SearchExhausted(String reservationId, String facilityId, Reservation reservationDto, List<String> resourceIds) implements ReservationEvent {}

    @TypeName("reservation-selected")
    record ResourceSelected(int resourceIndex, String resourceId, String reservationId, String facilityId, Reservation reservationDto) implements ReservationEvent {}
    @TypeName("booked")
    record Booked(String resourceId, String reservationId, Reservation reservationDto, List<String> resourceIds) implements ReservationEvent {}
}