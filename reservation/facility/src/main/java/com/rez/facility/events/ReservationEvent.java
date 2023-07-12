package com.rez.facility.events;

import com.rez.facility.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.util.List;

public sealed interface ReservationEvent {

    @TypeName("search-exhausted")
    record SearchExhausted(String reservationId, String facilityId, Reservation reservation, List<String> resourceIds) implements ReservationEvent {}

    @TypeName("booked")
    record Booked(String resourceId, String reservationId, Reservation reservation, List<String> resourceIds) implements ReservationEvent {}
}