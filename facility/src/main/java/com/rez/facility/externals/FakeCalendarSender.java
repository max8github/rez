package com.rez.facility.externals;

import com.google.api.services.calendar.Calendar;
import com.rez.facility.api.Mod;
import com.rez.facility.spi.CalendarSender;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class FakeCalendarSender implements CalendarSender {

    @Override
    public CompletionStage<Mod.ReservationResult> saveToGoogle(Calendar service, Mod.EventDetails eventDetails) {
        log.info("fake save to Google for reservation id {}", eventDetails.reservationId());
        String facilityCalendarUrl = CalendarSender.calendarUrl(eventDetails.resourceIds());
        return CompletableFuture.completedStage(new Mod.ReservationResult(eventDetails, "DONE", facilityCalendarUrl));
    }

    @Override
    public CompletionStage<Mod.CalendarEventDeletionResult> deleteFromGoogle(Calendar service, String calendarId, String calEventId) {
        log.info("fake delete of reservationId {} from google calendar {}", calEventId, calendarId);
        return CompletableFuture.completedStage(new Mod.CalendarEventDeletionResult(calendarId, calEventId));
    }
}
