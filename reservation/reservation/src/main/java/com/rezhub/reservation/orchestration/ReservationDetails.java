package com.rezhub.reservation.orchestration;

import java.time.LocalDateTime;

/**
 * Current state of a reservation, returned by ReservationGateway.get().
 */
public record ReservationDetails(
    String reservationId,
    String status,
    String resourceId,
    LocalDateTime dateTime
) {}
