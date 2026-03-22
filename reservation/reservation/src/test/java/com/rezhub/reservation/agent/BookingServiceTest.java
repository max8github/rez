package com.rezhub.reservation.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BookingService validation logic.
 *
 * These tests cover the fast early-exit paths that return before any
 * ComponentClient or TimerScheduler calls, so nulls are safe to pass.
 *
 * Note: sender-must-be-player rule tests (admin bypass, non-admin rejection)
 * will be added in the member sprint when BoundBookingService is introduced.
 */
class BookingServiceTest {

    // Early-exit paths never reach componentClient / timerScheduler, so nulls are safe.
    private final BookingService service = new BookingService(null, null);

    @Test
    void bookCourt_invalidDateFormat_returnsErrorMessage() {
        String result = service.bookCourt("facility-1", "not-a-date", "Max", "chat-1");

        assertThat(result).contains("Invalid date/time format");
        assertThat(result).contains("ISO-8601");
    }

    @Test
    void bookCourt_emptyPlayerNames_returnsErrorMessage() {
        String result = service.bookCourt("facility-1", "2026-03-22T11:00:00", "", "chat-1");

        assertThat(result).contains("At least one player name");
    }

    @Test
    void bookCourt_blankPlayersAfterSplit_returnsErrorMessage() {
        // "  ,  " splits into blanks that are filtered out, leaving an empty list
        String result = service.bookCourt("facility-1", "2026-03-22T11:00:00", "  ,  ", "chat-1");

        assertThat(result).contains("At least one player name");
    }

    @Test
    void checkAvailability_invalidDateFormat_returnsErrorMessage() {
        String result = service.checkAvailability("facility-1", "tomorrow");

        assertThat(result).contains("Invalid date/time format");
    }
}
