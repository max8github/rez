package com.rez.facility.api;

import com.rez.facility.spi.CalendarSender;
import com.rez.facility.spi.NotificationSender;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.WebClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class DelegatingServiceAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);

    final private WebClient webClient;

    final private CalendarSender calendarSender;
    final private NotificationSender notificationSender;


    public DelegatingServiceAction(WebClientProvider webClientProvider, CalendarSender calendarSender, NotificationSender notificationSender) {
        this.webClient = webClientProvider.webClientFor("twist");
        this.calendarSender = calendarSender;
        this.notificationSender = notificationSender;
    }

    CompletableFuture<String> messageTwistAccept(CalendarSender.ReservationResult result) {
        log.info("called messageTwist for reservation id {}", result.vo().reservationId());
        List<String> attendees = result.vo().reservation().emails();
        String time = result.vo().reservation().timeSlot() + "";
        String date = result.vo().reservation().date().toString();
        String messageContent = "Reservation Confirmed, id: %s.  Date: %s, Time: %s,  Attendees: %s"
                .formatted(result.vo().reservationId(), date, time, String.join(",", attendees));
        log.debug("Message content: {}", messageContent);
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
                """.formatted(messageContent, result.url());
        log.debug("Message body: {}", body);
        return notificationSender.messageTwist(webClient, body);
    }

    CompletableFuture<String> messageTwistReject(CalendarSender.ReservationResult result) {
        log.info("Messaging Twist back with UNAVAILABLE for reservation id {}", result.vo().reservationId());
        String time = result.vo().reservation().timeSlot() + "";
        String date = result.vo().reservation().date() + "";
        String messageContent = "Reservation rejected." + " Date: " + date +
                " Time: " + time + " are unavailable";
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
                """.formatted(messageContent, result.url());
        log.debug("Message body: {}", body);
        return notificationSender.messageTwist(webClient, body);
    }

    /**
     * This is the incoming webhook: Kalix -> Twist.<br>
     * It is used for posting a confirmation to Twist that something happened.
     */
    CompletableFuture<String> messageCancelToTwist(CalendarSender.CalendarEventDeletionResult result,
                                                   List<String> resourceIds) {
        log.info("Messaging Twist confirming cancellation of reservation id {} from calendar {}",
                result.calEventId(), result.calendarId());
//        String messageContent = "Reservation {} cancelled.".formatted(result.calEventId());
        String messageContent = "Reservation %s was cancelled.".formatted(result.calEventId());
        log.info("Message: '{}'", messageContent);
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
                """.formatted(messageContent, CalendarSender.calendarUrl(resourceIds));
        log.debug("Message body: {}", body);
        return notificationSender.messageTwist(webClient, body);
    }

    public Effect<String> on(ReservationEvent.Booked event) throws Exception {
        var resourceId = event.resourceId();
        String reservationId = event.reservationId();
        Mod.Reservation reservation = event.reservation();
        List<String> resourceIds = event.resourceIds();
        String facilityId = "facilityId";
        // todo: here i need: resource name (not id) and type, facility address and name (not id),
        var eventDetails = new CalendarSender.EventDetails(resourceId, reservationId, facilityId, reservation, resourceIds);
        var stageGoogle = calendarSender.saveToGoogle(eventDetails);
        var stage = stageGoogle.thenCompose(this::messageTwistAccept);
        return effects().asyncReply(stage);
    }

    public Effect<String> on(ReservationEvent.SearchExhausted event) {
        var eventDetails = new CalendarSender.EventDetails("", event.reservationId(), event.facilityId(),
                event.reservation(), event.resourceIds());
        var result = new CalendarSender.ReservationResult(eventDetails, "UNAVAILABLE", CalendarSender.calendarUrl(event.resourceIds()));
        return effects().asyncReply(messageTwistReject(result));
    }

    public Effect<String> on(ReservationEvent.ReservationCancelled event) throws IOException {
        String calendarId = event.resourceId() + "@group.calendar.google.com";
        String calEventId = event.reservationId();
        var stageGoogle = calendarSender.deleteFromGoogle(calendarId, calEventId);
        var stage = stageGoogle.thenCompose(c -> messageCancelToTwist(c, event.resourceIds()));
        return effects().asyncReply(stage);
    }
}