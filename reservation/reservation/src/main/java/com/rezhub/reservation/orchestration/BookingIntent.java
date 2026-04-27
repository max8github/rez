package com.rezhub.reservation.orchestration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Structured booking intent extracted by the AI agent from a user message.
 *
 * requestedSubjects is intentionally generic:
 *   - court booking: specific court names or empty (any court)
 *   - supplier booking: specific supplier names or filters
 */
public record BookingIntent(
    BookingAction action,
    LocalDateTime dateTime,
    Integer durationMinutes,
    List<String> participantNames,
    List<String> requestedSubjects,
    String reservationId,
    Map<String, String> attributes
) {
    public enum BookingAction { BOOK, CHECK_AVAILABILITY, CANCEL, GET_DETAILS }
}
