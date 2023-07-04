package com.rez.facility.api;

import com.rez.facility.spi.CalendarSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DelegatingServiceActionTest {

    @Test
    void testListToString() {
        List<String> attendees = List.of("Bob", "Tomas", "John");
        String messageContent = String.join(",", attendees);
        assertEquals("Bob,Tomas,John", messageContent);
    }

    @Test
    void testCalendarUrl() {
        List<String> resourceIds = List.of("3d228lvsdmdjmj79662t8r1fh4", "63hd39cd9ppt8tajp76vglt394");
        String calendarUrl = CalendarSender.calendarUrl(resourceIds);
        String expectedCalendarUrl = "https://calendar.google.com/calendar/u/0/embed?ctz=Europe/Berlin&src=3d228lvsdmdjmj79662t8r1fh4@group.calendar.google.com&src=63hd39cd9ppt8tajp76vglt394@group.calendar.google.com";
        System.out.println("resourceIds = " + resourceIds);
        System.out.println("calendarUrl = " + calendarUrl);
        assertEquals(expectedCalendarUrl, calendarUrl);
    }
}