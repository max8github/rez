package com.rezhub.reservation.reservation;

import akka.javasdk.annotations.TypeName;

/**
 * Public event types published to the "reservation-outcomes" service stream.
 * Consumed by Hit (and any other Akka service in the same project) to react to booking results.
 */
public sealed interface ReservationOutcomeEvent {

    @TypeName("reservation-outcome-fulfilled")
    record Fulfilled(String reservationId, String resourceId) implements ReservationOutcomeEvent {}

    @TypeName("reservation-outcome-rejected")
    record Rejected(String reservationId) implements ReservationOutcomeEvent {}
}
