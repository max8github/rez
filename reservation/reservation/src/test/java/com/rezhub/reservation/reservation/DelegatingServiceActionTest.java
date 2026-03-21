package com.rezhub.reservation.reservation;

import com.rezhub.reservation.spi.CalendarSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class DelegatingServiceActionTest {

    @Test
    void testListToString() {
        List<String> attendees = List.of("Bob", "Tomas", "John");
        String messageContent = String.join(",", attendees);
        assertEquals("Bob,Tomas,John", messageContent);
    }

    @Test
    void testCalendarUrl() {
        String id1 = "63hd39cd9ppt8tajp76vglt394@group.calendar.google.com";
        String id2 = "3d228lvsdmdjmj79662t8r1fh4@group.calendar.google.com";
        List<String> calendarIds = List.of(id1, id2);
        String calendarUrl = CalendarSender.calendarUrlFromIds(calendarIds);
        System.out.println("calendarIds = " + calendarIds);
        System.out.println("calendarUrl = " + calendarUrl);

        // Skip test if config isn't loaded (returns default URL)
        assumeFalse(calendarUrl.equals("http://example.com"),
            "Skipping test - google config not available in test context");

        String expectedCalendarUrl_12 = "https://calendar.google.com/calendar/u/0/embed?ctz=Europe/Berlin&src=" + id1
          + "&src=" + id2;
        String expectedCalendarUrl_21 = "https://calendar.google.com/calendar/u/0/embed?ctz=Europe/Berlin&src=" + id2
          + "&src=" + id1;
        assertTrue(calendarUrl.equals(expectedCalendarUrl_21) || calendarUrl.equals(expectedCalendarUrl_12));
    }
}