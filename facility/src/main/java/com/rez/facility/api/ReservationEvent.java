package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

import java.util.List;

public sealed interface ReservationEvent {
    @TypeName("reservation-initiated")
    record ReservationInitiated(String reservationId, String facilityId, Mod.Reservation reservation,
                                List<String> resources) implements ReservationEvent {}
    @TypeName("reservation-cancelled")
    record ReservationCancelled(String reservationId, String facilityId, Mod.Reservation reservation, String resourceId,
                                List<String> resourceIds) implements ReservationEvent {}
    @TypeName("cancel-requested")
    record CancelRequested(String reservationId, String facilityId, String resourceId, int timeSlot) implements ReservationEvent {}

    @TypeName("search-exhausted")
    record SearchExhausted(String reservationId, String facilityId, Mod.Reservation reservation, List<String> resourceIds) implements ReservationEvent {}

    @TypeName("reservation-selected")
    record ResourceSelected(int resourceIndex, String resourceId, String reservationId, String facilityId, Mod.Reservation reservation) implements ReservationEvent {}
    @TypeName("booked")
    record Booked(String resourceId, String reservationId, Mod.Reservation reservation, List<String> resourceIds) implements ReservationEvent {}
}