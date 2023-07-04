package com.rez.facility.externals;

import com.rez.facility.spi.CalendarSender;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class FakeCalendarSender implements CalendarSender {

    @Override
    public CompletionStage<ReservationResult> saveToGoogle(EventDetails eventDetails) {
        log.info("fake save to Google for reservation id {}", eventDetails.reservationId());
        String facilityCalendarUrl = CalendarSender.calendarUrl(eventDetails.resourceIds());
        return CompletableFuture.completedStage(new ReservationResult(eventDetails, "DONE", facilityCalendarUrl));
    }

    @Override
    public CompletionStage<CalendarEventDeletionResult> deleteFromGoogle(String calendarId, String calEventId) {
        log.info("fake delete of reservationId {} from google calendar {}", calEventId, calendarId);
        return CompletableFuture.completedStage(new CalendarEventDeletionResult(calendarId, calEventId));
    }
}
