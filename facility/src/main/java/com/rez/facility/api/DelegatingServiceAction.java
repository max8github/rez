package com.rez.facility.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.*;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class DelegatingServiceAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);
    private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    /**
     * Directory to store authorization tokens for this application.
     */
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";


    final private Calendar service;

    public DelegatingServiceAction() {
        service = setupService();
    }

    /**
     * Build a new authorized API client service.
     * @return the Calendar service
     */
    private Calendar setupService() {
        final NetHttpTransport httpTransport;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
        // Load client secrets.
        Credential credentials = null;
        try (InputStream in = DelegatingServiceAction.class.getResourceAsStream(CREDENTIALS_FILE_PATH)) {
            if (in == null) {
                throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
            }
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = null;
            flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

            credentials = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Calendar service = new Calendar.Builder(httpTransport, JSON_FACTORY, credentials)
                .setApplicationName(APPLICATION_NAME)
                .build();
        return service;
    }

    public Effect<String> on(ReservationEvent.Booked event) throws IOException {
        var resourceId = event.resourceId();
        var command = new ResourceEntity.InquireBooking(resourceId, event.reservationId(), "facilityId", event.reservation());
        var result = saveToGoogle(command);
        return effects().reply(result);
    }

    private String saveToGoogle(ResourceEntity.InquireBooking command) throws IOException {

        Event event = new Event()
                .setSummary("Resource Reserved")
                .setLocation("Tennisclub Ladenburg e.V., Römerstadion, Ladenburg, Germany")//todo: facility address here
                .setDescription(command.reservation().username() + ": tennis court reservation");

        var interval = convertSlotIntoStartEndDate(command.reservation());
        event.setStart(interval[0]);
        event.setEnd(interval[1]);

//        String[] recurrence = new String[]{"RRULE:FREQ=DAILY;COUNT=3"};
//        event.setRecurrence(Arrays.asList(recurrence));

        EventAttendee[] attendees = new EventAttendee[]{
                new EventAttendee().setEmail(command.reservation().username()),
        };
        event.setAttendees(Arrays.asList(attendees));

        EventReminder[] reminderOverrides = new EventReminder[]{
                new EventReminder().setMethod("email").setMinutes(24 * 60),
                new EventReminder().setMethod("popup").setMinutes(10),
        };
        Event.Reminders reminders = new Event.Reminders()
                .setUseDefault(false)
                .setOverrides(Arrays.asList(reminderOverrides));
        event.setReminders(reminders);

        String calendarId = "primary";
        String eventId = command.reservationId();
        log.info("reservationId = " + eventId);
        String found = isFound(service, calendarId, eventId);
        if(!found.isEmpty()) {
            log.info("event '" + found + "' had already been booked: nothing to do, all good");
            return eventId;
        }
        boolean slotFree = isSlotAvailable(service, calendarId, event);

        if(slotFree) {
            event = service.events().insert(calendarId, event).execute();
            System.out.printf("Event inserted: %s\n", event.getHtmlLink());
            return event.getHtmlLink();
        } else {
            log.info("Time slot was already taken: UNAVAILABLE for reservation id " + eventId);
            return "";
        }
    }

    static String isFound(Calendar service, String calendarId, String eventId) throws IOException {
        Event found = null;
        try {
            found = service.events().get(calendarId, eventId).execute();
        } catch (GoogleJsonResponseException e) {
            if(e.getStatusCode() == 404) {
                return "";//it is not found, sending nothing back, empty
            }
            throw new IOException(e);
        }
        //if code gets here, this is more complicated, because the event could be cancelled.
        //Even when cancelled, the eventId is still present and cannot be inserted again (you would get 409 conflict).
        //So, you have to check if the same users booked the same time or not.
        if(!found.getStatus().equals("cancelled")) {
            return found.getHtmlLink();//the booking is found, it was already there, it was not cancelled, sending link.
        }
        throw new IOException("Event cannot be inserted with the same identifier: please choose a different one.");
    }

    static boolean isSlotAvailable(Calendar service, String calendarId, Event event) throws IOException {
        var start = event.getStart().getDateTime();
        var end = event.getEnd().getDateTime();
        Events events = service.events().list(calendarId)
                .setTimeMin(start)
                .setTimeMax(end)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        List<Event> items = events.getItems();
        return items.isEmpty();
    }

    private static EventDateTime[] convertSlotIntoStartEndDate(Mod.Reservation reservation) {
        int slot = reservation.timeSlot() < 23 ? reservation.timeSlot() : 22;//todo
        return new EventDateTime[]{getEventDateTime(slot), getEventDateTime(slot + 1)};
    }

    private static EventDateTime getEventDateTime(int slot) {
        String hour = String.format("%02d", slot);
        DateTime dateTime = new DateTime("2023-06-03T"+ hour +":00:00-07:00");
        EventDateTime eventDateTime = new EventDateTime()
                .setDateTime(dateTime)
                .setTimeZone("America/Los_Angeles");
        return eventDateTime;
    }
}