package com.rezhub.reservation.orchestration;

/**
 * Receipt returned when a reservation is submitted to the booking engine.
 * The booking outcome is delivered asynchronously via the notification path.
 */
public record ReservationHandle(String reservationId) {}
