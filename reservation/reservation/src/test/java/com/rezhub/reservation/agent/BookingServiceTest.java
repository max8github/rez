package com.rezhub.reservation.agent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BookingTools validation logic.
 *
 * These tests cover the fast early-exit paths that return before any
 * ComponentClient or service calls, so nulls are safe to pass.
 */
class BookingServiceTest {

    // Early-exit paths never reach service or componentClient, so nulls are safe.
    private final BookingTools tools = new BookingTools(null, null, null);

    @Test
    void bookCourt_invalidDateFormat_returnsErrorMessage() {
        String result = tools.bookCourt("facility-1", "not-a-date", "Max", "chat-1");

        assertThat(result).contains("Invalid date/time format");
        assertThat(result).contains("ISO-8601");
    }

    @Test
    void bookCourt_emptyPlayerNames_returnsErrorMessage() {
        String result = tools.bookCourt("facility-1", "2026-03-22T11:00:00", "", "chat-1");

        assertThat(result).contains("At least one player name");
    }

    @Test
    void bookCourt_blankPlayersAfterSplit_returnsErrorMessage() {
        String result = tools.bookCourt("facility-1", "2026-03-22T11:00:00", "  ,  ", "chat-1");

        assertThat(result).contains("At least one player name");
    }

    @Test
    void checkAvailability_invalidDateFormat_returnsErrorMessage() {
        String result = tools.checkAvailability("facility-1", "tomorrow");

        assertThat(result).contains("Invalid date/time format");
    }

    @Test
    void resolveDateTime_parsesItalianTomorrow() {
        Optional<LocalDateTime> result = BookingTools.resolveNaturalDateTime(
            "Puoi prenotarmi un campo domani alle 18 per giocare con Taddeo?",
            ZoneId.of("Europe/Rome"),
            ZonedDateTime.of(2026, 4, 5, 12, 0, 0, 0, ZoneId.of("Europe/Rome")));

        assertThat(result).contains(LocalDateTime.of(2026, 4, 6, 18, 0));
    }

    @Test
    void resolveDateTime_parsesEnglishTomorrowWithAmPm() {
        Optional<LocalDateTime> result = BookingTools.resolveNaturalDateTime(
            "Book tomorrow at 6pm",
            ZoneId.of("Europe/Rome"),
            ZonedDateTime.of(2026, 4, 5, 12, 0, 0, 0, ZoneId.of("Europe/Rome")));

        assertThat(result).contains(LocalDateTime.of(2026, 4, 6, 18, 0));
    }

    @Test
    void resolveDateTime_parsesItalianWeekday() {
        Optional<LocalDateTime> result = BookingTools.resolveNaturalDateTime(
            "martedi alle 19",
            ZoneId.of("Europe/Rome"),
            ZonedDateTime.of(2026, 4, 5, 12, 0, 0, 0, ZoneId.of("Europe/Rome")));

        assertThat(result).contains(LocalDateTime.of(2026, 4, 7, 19, 0));
    }

    @Test
    void resolveDateTime_returnsEmptyWhenTimeMissing() {
        Optional<LocalDateTime> result = BookingTools.resolveNaturalDateTime(
            "domani",
            ZoneId.of("Europe/Rome"),
            ZonedDateTime.of(2026, 4, 5, 12, 0, 0, 0, ZoneId.of("Europe/Rome")));

        assertThat(result).isEmpty();
    }

    @Test
    void checkAvailability_pastDate_returnsGuardMessage() {
        String result = tools.checkAvailability("facility-1", "2026-04-01T18:00:00");

        assertThat(result).contains("in the past");
    }
}
