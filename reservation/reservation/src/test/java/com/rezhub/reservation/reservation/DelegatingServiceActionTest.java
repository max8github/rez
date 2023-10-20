package com.rezhub.reservation.reservation;

import com.rezhub.reservation.spi.CalendarSender;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelegatingServiceActionTest {

    @Test
    void testListToString() {
        List<String> attendees = List.of("Bob", "Tomas", "John");
        String messageContent = String.join(",", attendees);
        assertEquals("Bob,Tomas,John", messageContent);
    }

    @Test
    void testCalendarUrl() {
        String id1 = "63hd39cd9ppt8tajp76vglt394";
        String id2 = "3d228lvsdmdjmj79662t8r1fh4";
        Set<String> resourceIds = Set.of(id1, id2);
        String calendarUrl = CalendarSender.calendarUrl(resourceIds);
        String expectedCalendarUrl_12 = "https://calendar.google.com/calendar/u/0/embed?ctz=Europe/Berlin&src=" + id1
          + "@group.calendar.google.com&src=" + id2 + "@group.calendar.google.com";
        String expectedCalendarUrl_21 = "https://calendar.google.com/calendar/u/0/embed?ctz=Europe/Berlin&src=" + id2
          + "@group.calendar.google.com&src=" + id1 + "@group.calendar.google.com";
        System.out.println("resourceIds = " + resourceIds);
        System.out.println("calendarUrl = " + calendarUrl);
        assertTrue(calendarUrl.equals(expectedCalendarUrl_21) || calendarUrl.equals(expectedCalendarUrl_12));
    }
}