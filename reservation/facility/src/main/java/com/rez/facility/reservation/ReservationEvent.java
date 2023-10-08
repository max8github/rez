package com.rez.facility.reservation;

import com.rez.facility.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.time.LocalDateTime;
import java.util.List;

public sealed interface ReservationEvent {
    @TypeName("reservation-initiated")
    record ReservationInitiated(String reservationId, String facilityId, Reservation reservation,
                                List<String> resources) implements ReservationEvent {}
    @TypeName("reservation-cancelled")
    record ReservationCancelled(String reservationId, String facilityId, Reservation reservation, String resourceId,
                                List<String> resourceIds) implements ReservationEvent {}
    @TypeName("cancel-requested")
    record CancelRequested(String reservationId, String facilityId, String resourceId, LocalDateTime dateTime) implements ReservationEvent {}

    @TypeName("search-exhausted")
    record SearchExhausted(String reservationId, String facilityId, Reservation reservation, List<String> resourceIds) implements ReservationEvent {}

    @TypeName("waiting")
    record Waiting(String reservationId, String resourceId) implements ReservationEvent {}
    @TypeName("keep-waiting")
    record KeepWaiting(String reservationId) implements ReservationEvent {}

    @TypeName("resource-responded")
    record ResourceResponded(String resourceId, String reservationId, Reservation reservation, boolean available, String facilityId) implements ReservationEvent {}
    @TypeName("candidate-flagged")
    record ResourceSelected(String resourceId, String reservationId, Reservation reservation, String facilityId) implements ReservationEvent {}

    @TypeName("booked")
    record Booked(String resourceId, String reservationId, Reservation reservation, List<String> resourceIds,
                  String facilityId) implements ReservationEvent {}
}