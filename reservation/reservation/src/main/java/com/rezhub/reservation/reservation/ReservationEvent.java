package com.rezhub.reservation.reservation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rezhub.reservation.dto.Reservation;
import akka.javasdk.annotations.TypeName;

import java.time.LocalDateTime;
import java.util.Set;

public sealed interface ReservationEvent {

    @TypeName("reservation-initiated")
    record Inited(String reservationId, Reservation reservation,
                  Set<String> resourceIds, String recipientId,
                  String originSystem) implements ReservationEvent {
        @JsonCreator
        static Inited create(
                @JsonProperty("reservationId") String reservationId,
                @JsonProperty("reservation") Reservation reservation,
                @JsonProperty("resourceIds") Set<String> resourceIds,
                @JsonProperty("recipientId") String recipientId,
                @JsonProperty("originSystem") String originSystem) {
            return new Inited(reservationId, reservation, resourceIds, recipientId, originSystem);
        }
    }

    @TypeName("reservation-cancelled")
    record ReservationCancelled(String reservationId, Reservation reservation, String resourceId,
                                Set<String> resourceIds, String recipientId) implements ReservationEvent {}

    @TypeName("cancel-requested")
    record CancelRequested(String reservationId, String resourceId, LocalDateTime dateTime) implements ReservationEvent {}

    @TypeName("search-exhausted")
    record SearchExhausted(String reservationId, Reservation reservation, Set<String> resourceIds,
                           String recipientId, String originSystem) implements ReservationEvent {
        @JsonCreator
        static SearchExhausted create(
                @JsonProperty("reservationId") String reservationId,
                @JsonProperty("reservation") Reservation reservation,
                @JsonProperty("resourceIds") Set<String> resourceIds,
                @JsonProperty("recipientId") String recipientId,
                @JsonProperty("originSystem") String originSystem) {
            return new SearchExhausted(reservationId, reservation, resourceIds, recipientId, originSystem);
        }
    }

    @TypeName("rejected")
    record Rejected(String reservationId, String resourceId) implements ReservationEvent {}

    @TypeName("resource-responded")
    record AvailabilityReplied(String resourceId, String reservationId, Reservation reservation, boolean available) implements ReservationEvent {}

    @TypeName("candidate-flagged")
    record ResourceSelected(String resourceId, String reservationId, Reservation reservation) implements ReservationEvent {}

    @TypeName("booked")
    record Fulfilled(String resourceId, String reservationId, Reservation reservation, Set<String> resourceIds,
                     String recipientId, String originSystem) implements ReservationEvent {
        @JsonCreator
        static Fulfilled create(
                @JsonProperty("resourceId") String resourceId,
                @JsonProperty("reservationId") String reservationId,
                @JsonProperty("reservation") Reservation reservation,
                @JsonProperty("resourceIds") Set<String> resourceIds,
                @JsonProperty("recipientId") String recipientId,
                @JsonProperty("originSystem") String originSystem) {
            return new Fulfilled(resourceId, reservationId, reservation, resourceIds, recipientId, originSystem);
        }
    }
}
