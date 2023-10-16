package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.time.LocalDateTime;
import java.util.List;

public sealed interface ReservationEvent {
    @TypeName("reservation-initiated")
    record Inited(String reservationId, String facilityId, Reservation reservation,
                  List<String> resources) implements ReservationEvent {}
    @TypeName("reservation-cancelled")
    record ReservationCancelled(String reservationId, String facilityId, Reservation reservation, String resourceId,
                                List<String> resourceIds) implements ReservationEvent {}
    @TypeName("cancel-requested")
    record CancelRequested(String reservationId, String facilityId, String resourceId, LocalDateTime dateTime) implements ReservationEvent {}

    @TypeName("search-exhausted")
    record SearchExhausted(String reservationId, String facilityId, Reservation reservation, List<String> resourceIds) implements ReservationEvent {}

    @TypeName("rejectedWithNext")
    record RejectedWithNext(String reservationId, String resourceId, String nextResourceId, String facilityId) implements ReservationEvent {}
    @TypeName("rejected")
    record Rejected(String reservationId, String resourceId) implements ReservationEvent {}

    @TypeName("resource-responded")
    record AvailabilityReplied(String resourceId, String reservationId, Reservation reservation, boolean available, String facilityId) implements ReservationEvent {}
    @TypeName("candidate-flagged")
    record ResourceSelected(String resourceId, String reservationId, Reservation reservation, String facilityId) implements ReservationEvent {}

    @TypeName("booked")
    record Fulfilled(String resourceId, String reservationId, Reservation reservation, List<String> resourceIds,
                     String facilityId) implements ReservationEvent {}
}