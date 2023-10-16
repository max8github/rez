package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.time.LocalDateTime;
import java.util.Set;

public sealed interface ReservationEvent {
    @TypeName("reservation-initiated")
    record Inited(String reservationId, Reservation reservation,
                  Set<String> resources) implements ReservationEvent {}
    @TypeName("reservation-cancelled")
    record ReservationCancelled(String reservationId, Reservation reservation, String resourceId,
                                Set<String> resourceIds) implements ReservationEvent {}
    @TypeName("cancel-requested")
    record CancelRequested(String reservationId, String resourceId, LocalDateTime dateTime) implements ReservationEvent {}

    @TypeName("search-exhausted")
    record SearchExhausted(String reservationId, Reservation reservation, Set<String> resourceIds) implements ReservationEvent {}

    @TypeName("rejectedWithNext")
    record RejectedWithNext(String reservationId, String resourceId, String nextResourceId) implements ReservationEvent {}
    @TypeName("rejected")
    record Rejected(String reservationId, String resourceId) implements ReservationEvent {}

    @TypeName("resource-responded")
    record AvailabilityReplied(String resourceId, String reservationId, Reservation reservation, boolean available) implements ReservationEvent {}
    @TypeName("candidate-flagged")
    record ResourceSelected(String resourceId, String reservationId, Reservation reservation) implements ReservationEvent {}

    @TypeName("booked")
    record Fulfilled(String resourceId, String reservationId, Reservation reservation, Set<String> resourceIds) implements ReservationEvent {}
}