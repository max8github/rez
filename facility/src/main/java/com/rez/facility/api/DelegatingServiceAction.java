package com.rez.facility.api;

import akka.japi.Pair;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.WebClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class DelegatingServiceAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);

    final private WebClient webClient;

    final private Calendar service;

    public DelegatingServiceAction(WebClientProvider webClientProvider, Calendar calendar) {
        this.webClient = webClientProvider.webClientFor("twist");
        this.service = calendar;
    }

    public CompletableFuture<String> messageTwistAccept(ReservationResult result) {
        log.info("called messageTwist for reservation id {}", result.vo().reservationId());
        List<String> attendees = result.vo().reservation().emails();
        String time = result.vo().reservation().timeSlot() + "";
        String date = result.vo().reservation().date().toString();
        String messageContent = "Reservation " + result.result + ". Date: %s, Time: %s, Attendees: %s" // todo: the flow of confirmation/rejection and messages to the user does not fully work ...
                .formatted(date, time, attendees);
        return messageTwist(result, messageContent);
    }

    public CompletableFuture<String> messageTwistReject(ReservationResult result) {
        log.info("messaged Twist for reservation id {} UNAVAILABLE", result.vo().reservationId());
        String time = result.vo().reservation().timeSlot() + "";
        String date = result.vo().reservation().date() + "";
        String messageContent = "Reservation rejected." + " Date: " + date +
                " Time: " + time + " are unavailable";
        return messageTwist(result, messageContent);
    }

    /**
     * This is the incoming webhook: Kalix -> Twist.<br>
     * It is used for posting a confirmation to Twist that something happened.
     */
    private CompletableFuture<String> messageTwist(ReservationResult p, String message) {
        Config twistConfig = ConfigFactory.defaultApplication().getConfig("twist");
        String url = twistConfig.getString("url");//todo: validate the url here or else call will fail (painful)
        String install_id = twistConfig.getString("install_id");
        String install_token = System.getenv("INSTALL_TOKEN");
        String uri = url + "?install_id=" + install_id + "&install_token=" + install_token;
        String body =
                """
                    {
                       "content":  "%s",
                       "actions": [
                         {
                           "action": "open_url",
                           "url": "%s",
                           "type": "action",
                           "button_text": "Go to Calendar"
                         }
                       ]
                     }
                """.formatted(message, p.url());
        return webClient
                .post().uri(uri)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class).toFuture();
    }


    public CompletableFuture<String> fakeMessageTwist(Pair<String, EventDetails> p) {
        log.info("called fakeMessageTwist for reservation id  {}", p.first());
        return CompletableFuture.completedFuture("hi");
    }

    public Effect<String> on(ReservationEvent.Booked event) throws Exception {
        var resourceId = event.resourceId();
        String reservationId = event.reservationId();
        Mod.Reservation reservation = event.reservation();
        String facilityId = "facilityId";
        var eventDetails = new EventDetails(resourceId, reservationId, facilityId, reservation);
        var stageGoogle = saveToGoogle(eventDetails);
//        var stageGoogle = fakeSaveToGoogle(eventDetails);
        var stage = stageGoogle.thenCompose(this::messageTwistAccept);
        return effects().asyncReply(stage);
    }

    public Effect<String> on(ReservationEvent.SearchExhausted event) throws Exception {
        //todo: refactor this part, it can be consolidated better, also semantically (class record names)
        var command = new EventDetails("111", event.reservationId(), "facilityId", event.reservation());
        var result = new ReservationResult(command, "UNAVAILABLE", "http://example.com");
        return effects().asyncReply(messageTwistReject(result));//todo
    }

    private CompletionStage<ReservationResult>
    fakeSaveToGoogle(EventDetails eventDetails) {
        log.info("called fakeSaveToGoogle for reservation id {}", eventDetails.reservationId());
        String fakeUrl = "http://example.com";
        return CompletableFuture.completedStage(new ReservationResult(eventDetails, "DONE", fakeUrl));
    }

    private CompletionStage<ReservationResult>
    saveToGoogle(EventDetails eventDetails) throws IOException {
//        String calendarId = "3d228lvsdmdjmj79662t8r1fh4@group.calendar.google.com";//court 1 calendar id = resource id
//        String calendarId = "63hd39cd9ppt8tajp76vglt394@group.calendar.google.com";//court 2 calendar id = resource id
        String calendarId = eventDetails.resourceId() + "@group.calendar.google.com";
        String calEventId = eventDetails.reservationId();
        log.info("reservationId = " + calEventId);
        String found = isFound(service, calendarId, calEventId);
        if(!found.isEmpty()) {
            log.info("event '" + found + "' had already been booked: nothing to do, all good");
            String calendarUrl = "http://example.com";
            return CompletableFuture.completedStage(
                    new ReservationResult(eventDetails, "ALREADY_BOOKED", calendarUrl));
        }
        var interval = convertSlotIntoStartEndDate(eventDetails.reservation());
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
                .setSummary("Resource Reserved")
                .setId(calEventId)
                .setLocation("Tennisclub Ladenburg e.V., Römerstadion, Ladenburg, Germany")//todo: facility address here
                .setDescription(eventDetails.reservation().emails() + ": btoken resource reservation")
                .setStart(interval[0])
                .setEnd(interval[1])
                .setAttendees(Arrays.asList(attendees));
//            .setRecurrence(Arrays.asList(recurrence))
//                .setReminders(reminders);

        if(isSlotAvailable(service, calendarId, event)) {
            event = service.events().insert(calendarId, event).execute();
            log.info("Event inserted: {}", event.getHtmlLink());
            return CompletableFuture.completedStage(
                    new ReservationResult(eventDetails, "DONE", event.getHtmlLink()));
        } else {//should never happen, because only Kalix writes to the Calendar.
            var msg = "Time slot was already taken: UNAVAILABLE for reservation id " + calEventId;
            log.error(msg);
            return CompletableFuture.completedStage(
                    new ReservationResult(eventDetails, "UNAVAILABLE", "http://example.com"));
        }
    }

    private EventAttendee[] getAttendees(EventDetails eventDetails) {
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
        int slot = reservation.timeSlot() < 23 ? reservation.timeSlot() : 22;//todo validation, but need to use time ...
        LocalDate date = reservation.date();
        return new EventDateTime[]{getEventDateTime(date, slot), getEventDateTime(date,slot + 1)};
    }

    private static EventDateTime getEventDateTime(LocalDate date, int slot) {
        String dateF = date.format(DateTimeFormatter.ISO_DATE);
        String hour = String.format("%02d", slot);
        DateTime dateTime = new DateTime(dateF+"T"+ hour +":00:00+02:00");//todo
        return new EventDateTime()
                .setDateTime(dateTime)
                .setTimeZone("Europe/Zurich");//America/Los_Angeles, which is GMT -7
    }

    private record ReservationResult(EventDetails vo, String result, String url) {}
    private record EventDetails(String resourceId, String reservationId, String facilityId, Mod.Reservation reservation) {}
}