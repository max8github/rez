package com.rezhub.reservation.orchestration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * All information the reservation core needs to initiate a booking competition.
 * resourceIds are explicit — no facility lookup is performed inside the core.
 */
public record ReservationSubmission(
    String reservationId,
    String recipientId,
    String timezone,
    LocalDateTime dateTime,
    List<String> participants,
    Set<String> resourceIds,
    String originSystem
) {}
