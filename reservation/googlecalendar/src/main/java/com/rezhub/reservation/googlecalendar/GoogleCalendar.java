package com.rezhub.reservation.googlecalendar;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.rezhub.reservation.spi.CalendarSender;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
@Component
public class GoogleCalendar implements CalendarSender {

    private final Calendar service;

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendar.class);

    @Override
    public CompletionStage<ReservationResult> saveToGoogle(EventDetails eventDetails) throws IOException {
        String facilityCalendarUrl = CalendarSender.calendarUrl(eventDetails.resourceIds());
        String calendarId = eventDetails.resourceId() + "@group.calendar.google.com";
        String calEventId = eventDetails.reservationId();
        log.info("reservationId = " + calEventId);
        String found = isFound(service, calendarId, calEventId);
        if (!found.isEmpty()) {
            log.info("event '" + found + "' had already been booked: nothing to do, all good");
            return CompletableFuture.completedStage(
                    new ReservationResult(eventDetails, "ALREADY_BOOKED", facilityCalendarUrl));
        }
        var interval = toEventDateTimeInterval(eventDetails.dateTime());
        EventAttendee[] attendees = getAttendees(eventDetails);

        //        String[] recurrence = new String[]{"RRULE:FREQ=DAILY;COUNT=3"};

        EventReminder[] reminderOverrides = new EventReminder[]{
                new EventReminder().setMethod("email").setMinutes(24 * 60),
                new EventReminder().setMethod("popup").setMinutes(10),
        };
        Event.Reminders reminders = new Event.Reminders()
                .setUseDefault(false)
                .setOverrides(Arrays.asList(reminderOverrides));

        Event event = new Event()
//                .setSummary(eventDetails.reservationId()) // if I set it to "" it will say "(No Title)"
                .setSummary("Reserved")
                .setId(calEventId)
                .setLocation("Tennisclub Ladenburg e.V., Römerstadion, Ladenburg, Germany")//todo: facility address here
                .setDescription("Reservation " + eventDetails.reservationId()
                        + "\nfor: " + String.join(",", eventDetails.emails())
                        + "\n"
                        + "resource: " + eventDetails.resourceId()) //todo: type of resource here instead of just 'resource'
                .setStart(interval[0])
                .setEnd(interval[1])
                .setAttendees(Arrays.asList(attendees));
//            .setRecurrence(Arrays.asList(recurrence))
//                .setReminders(reminders);

        if (isSlotAvailable(service, calendarId, event)) {
            event = service.events().insert(calendarId, event).execute();
            log.info("Event {} inserted in calendars {}", event.getHtmlLink(), facilityCalendarUrl);
            return CompletableFuture.completedStage(
                    new ReservationResult(eventDetails, "DONE", facilityCalendarUrl));// todo: can also use event.getHtmlLink()
        } else {//should never happen, because only Kalix writes to the Calendar.
            var msg = "Time slot was already taken: UNAVAILABLE for reservation id " + calEventId;
            log.error(msg);
            return CompletableFuture.completedStage(
                    new ReservationResult(eventDetails, "UNAVAILABLE", facilityCalendarUrl));
        }
    }

    @Override
    public CompletionStage<CalendarEventDeletionResult> deleteFromGoogle(String calendarId, String calEventId) {
        log.info("deleting reservationId {} from google calendar {}", calEventId, calendarId);
        try {
            service.events().delete(calendarId, calEventId).execute();
        } catch (IOException e) {
            log.error("Delete of calendar event {} failed for calendar {}", calEventId, calendarId);
        }
        return CompletableFuture.completedStage(new CalendarEventDeletionResult(calendarId, calEventId));
    }

    private static EventAttendee[] getAttendees(EventDetails eventDetails) {
        //todo: get domain-wide authority for enabling this
//        EventAttendee[] attendees = eventDetails.reservation().emails() == null ?
//                new EventAttendee[0] :
//                eventDetails.reservation().emails().stream().map(s ->
//                        new EventAttendee().setEmail(s)).collect(Collectors.toList()).stream().toArray(EventAttendee[]::new);
        EventAttendee[] attendees = new EventAttendee[0];
        return attendees;

    }

    //todo: this method is not very useful, because it is never going to happen that two ids will conflict.
    //What this method should do is determine if the event is a duplicate or not.
    //It could determine that by checking start and end time and username.
    static String isFound(Calendar service, String calendarId, String eventId) throws IOException {
        Event found;
        try {
            found = service.events().get(calendarId, eventId).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                return "";//it is not found, sending nothing back, empty
            }
            throw new IOException(e);
        }
        //if code gets here, this is more complicated, because the event could be cancelled.
        //Even when cancelled, the eventId is still present and cannot be inserted again (you would get 409 conflict).
        //So, you have to check if the same users booked the same time or not.
        if (!found.getStatus().equals("cancelled")) {
            return found.getHtmlLink();//the booking is found, it was already there, it was not cancelled, sending link.
        }
        throw new IOException("Event cannot be inserted with the same identifier: please choose a different one.");
    }

    static boolean isSlotAvailable(Calendar service, String calendarId, Event event) throws IOException {
        var start = event.getStart().getDateTime();
        var end = event.getEnd().getDateTime();
        return service.events().list(calendarId)
                .setTimeMin(start)
                .setTimeMax(end)
                .setSingleEvents(true)
                .execute()
                .getItems()
                .isEmpty();
    }

    private static EventDateTime[] toEventDateTimeInterval(LocalDateTime dateTime) {
        // todo: end time depends on the timeSlot, which is in Reservation/Resource
        DateTime dateTimeStart = new DateTime(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        LocalDateTime endDateTime = dateTime.toLocalDate().atTime(dateTime.toLocalTime().plusHours(1L));
        DateTime dateTimeEnd = new DateTime(endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        EventDateTime start = new EventDateTime()
                .setDateTime(dateTimeStart)
                .setTimeZone("Europe/Zurich");//America/Los_Angeles, which is GMT -7
        EventDateTime end = new EventDateTime()
                .setDateTime(dateTimeEnd)
                .setTimeZone("Europe/Zurich");//America/Los_Angeles, which is GMT -7
        return new EventDateTime[] {start, end};
    }
}
