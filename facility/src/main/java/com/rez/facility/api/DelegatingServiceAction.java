package com.rez.facility.api;

import akka.japi.Pair;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.WebClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;


import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class DelegatingServiceAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);

    final private WebClient webClient;
    private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";


    final private Calendar service;

    public DelegatingServiceAction(WebClientProvider webClientProvider) {
        this.webClient = webClientProvider.webClientFor("twist");
        this.service = setupService();
    }

    /**
     * Build a new authorized API client service.
     * @return the Calendar service
     */
    private Calendar setupService() {
        HttpTransport httpTransport;
        try {
        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        //Build service account credential
        HttpRequestInitializer requestInitializer;
        try (InputStream in = DelegatingServiceAction.class.getResourceAsStream(CREDENTIALS_FILE_PATH)) {
            if (in == null) {
                throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
            }
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(in).createScoped(SCOPES);
            requestInitializer = new HttpCredentialsAdapter(googleCredentials);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new Calendar.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    //todo on InquireBooking: i am using this class where it is semantically wrong, like in DelegatingServiceAction. Another class should be used instead.
    public CompletableFuture<String> messageTwist(Pair<String, ResourceEntity.InquireBooking> p) {
        String attendees = p.second().reservation().username();
        String time = p.second().reservation().timeSlot() + "";

        String messageContent = "Reservation confirmed." +
                " Time: " + time + ", Attendees: " + attendees + ", URL: " + p.first();
        Config twistConfig = ConfigFactory.defaultApplication().getConfig("twist");
        String url = twistConfig.getString("url");
        String install_id = twistConfig.getString("install_id");
        String install_token = System.getenv("INSTALL_TOKEN");
        String uri = url + "?install_id=" + install_id + "&install_token=" + install_token;
        log.info(uri);
//        return CompletableFuture.completedFuture("ciao ok");
        return webClient
                .post().uri(uri)
                .bodyValue("{\n" +
                        "    \"title\": \"Book tennis courts\",\n" +
                        "    \"content\": \"" + messageContent + "\"\n" +
                        "} ")
                .retrieve()
                .bodyToMono(String.class).toFuture();
    }

    public Effect<String> on(ReservationEvent.Booked event) throws Exception {
        var resourceId = event.resourceId();
        var command = new ResourceEntity.InquireBooking(resourceId, event.reservationId(), "facilityId", event.reservation());
        // todo: unclear on how to best return the effect here, as we don't need to reply to anything here.
        var stage = saveToGoogle(command).thenCompose(this::messageTwist);
//        var stage = messageTwist(new Pair<>("test", command));
        return effects().asyncReply(stage);
    }

    private CompletionStage<Pair<String, ResourceEntity.InquireBooking>>
    saveToGoogle(ResourceEntity.InquireBooking command) throws IOException {
        String calendarId = "primary";
        String calEventId = command.reservationId();
        log.info("reservationId = " + calEventId);
        String found = isFound(service, calendarId, calEventId);
        if(!found.isEmpty()) {
            log.info("event '" + found + "' had already been booked: nothing to do, all good");
            return CompletableFuture.completedStage(new Pair<>(calEventId, command));
        }
        var interval = convertSlotIntoStartEndDate(command.reservation());
        EventAttendee[] attendees = new EventAttendee[]{
                new EventAttendee().setEmail(command.reservation().username()),
        };
//        String[] recurrence = new String[]{"RRULE:FREQ=DAILY;COUNT=3"};

        EventReminder[] reminderOverrides = new EventReminder[]{
                new EventReminder().setMethod("email").setMinutes(24 * 60),
                new EventReminder().setMethod("popup").setMinutes(10),
        };
        Event.Reminders reminders = new Event.Reminders()
                .setUseDefault(false)
                .setOverrides(Arrays.asList(reminderOverrides));

        Event event = new Event()
                .setSummary("Resource Reserved")
                .setId(calEventId)
                .setLocation("Tennisclub Ladenburg e.V., Römerstadion, Ladenburg, Germany")//todo: facility address here
                .setDescription(command.reservation().username() + ": tennis court reservation")
                .setStart(interval[0])
                .setEnd(interval[1]);
//            .setRecurrence(Arrays.asList(recurrence))
//                .setAttendees(Arrays.asList(attendees))
//                .setReminders(reminders);

        if(isSlotAvailable(service, calendarId, event)) {
            event = service.events().insert(calendarId, event).execute();
            log.info("Event inserted: {}", event.getHtmlLink());
            return CompletableFuture.completedStage(new Pair<>(event.getHtmlLink(), command));
        } else {//should never happen, because only Kalix writes to the Calendar.
            var msg = "Time slot was already taken: UNAVAILABLE for reservation id " + calEventId;
            log.error(msg);
            throw new IOException(msg);
        }
    }

    //todo: this method is not very useful, because it is never going to happen that two ids will conflict.
    //What this method should do is determine if the event is a duplicate or not.
    //It could determine that by checking start and end time and username.
    static String isFound(Calendar service, String calendarId, String eventId) throws IOException {
        Event found;
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
        return service.events().list(calendarId)
                .setTimeMin(start)
                .setTimeMax(end)
                .setSingleEvents(true)
                .execute()
                .getItems()
                .isEmpty();
    }

    private static EventDateTime[] convertSlotIntoStartEndDate(Mod.Reservation reservation) {
        int slot = reservation.timeSlot() < 23 ? reservation.timeSlot() : 22;//todo
        return new EventDateTime[]{getEventDateTime(slot), getEventDateTime(slot + 1)};
    }

    private static EventDateTime getEventDateTime(int slot) {
        String hour = String.format("%02d", slot);
        DateTime dateTime = new DateTime("2023-07-28T"+ hour +":00:00-07:00");//todo
        return new EventDateTime()
                .setDateTime(dateTime)
                .setTimeZone("America/Los_Angeles");
    }
}