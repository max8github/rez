package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.spi.CalendarSender;
import com.rezhub.reservation.spi.NotificationSender;
import kalix.javasdk.action.Action;
import kalix.spring.WebClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RequestMapping("/external")
@SuppressWarnings("unused")
public class DelegatingServiceAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);

    final private WebClient webClient;

    final private CalendarSender calendarSender;
    final private NotificationSender notificationSender;

    @SuppressWarnings("unused")
    public DelegatingServiceAction(WebClientProvider webClientProvider, CalendarSender calendarSender, NotificationSender notificationSender) {
        this.webClient = webClientProvider.webClientFor("twist");
        this.calendarSender = calendarSender;
        this.notificationSender = notificationSender;
    }

    CompletableFuture<String> messageTwistAccept(CalendarSender.ReservationResult result) {
        log.info("called messageTwist for reservation id {}", result.eventDetails().reservationId());
        List<String> attendees = result.eventDetails().emails();
        String time = result.eventDetails().dateTime().toString();
        String messageContent = "Reservation Confirmed, id: %s. Date/Time: %s,  Attendees: %s"
          .formatted(result.eventDetails().reservationId(), time, String.join(",", attendees));
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
        log.info("Messaging Twist back with UNAVAILABLE for reservation id {}", result.eventDetails().reservationId());
        String time = result.eventDetails().dateTime().toString();
        String messageContent = "Reservation rejected." + " Date/Time: " + time + " is unavailable";
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
            """.formatted(messageContent, CalendarSender.calendarUrl(new HashSet<>(resourceIds)));
        log.debug("Message body: {}", body);
        return notificationSender.messageTwist(webClient, body);
    }

    @PostMapping("/book")
    public Effect<String> book(@RequestBody Fulfilled event) throws Exception {
        Reservation reservationDto = event.reservation();
        String reservationId = event.reservationId();
        List<String> resourceIds = event.resourceIds();
        // todo: here i need the resource and facility details, not their ids:
        String resourceId = event.resourceId();
        String facilityId = event.facilityId();
        var eventDetails = new CalendarSender.EventDetails(resourceId, reservationId, facilityId, new HashSet<>(resourceIds),
          reservationDto.emails(), reservationDto.dateTime());
        var stageGoogle = calendarSender.saveToGoogle(eventDetails);
        var stage = stageGoogle.thenCompose(this::messageTwistAccept);
        return effects().asyncReply(stage);
    }

    @PostMapping("/unavailable")
    public Effect<String> unavailable(@RequestBody NotifySearchExhausted message) {
        var eventDetails = new CalendarSender.EventDetails("", message.reservationId(), message.facilityId(),
          new HashSet<>(message.resourceIds()),
          message.reservation().emails(), message.reservation().dateTime());
        var result = new CalendarSender.ReservationResult(eventDetails, "UNAVAILABLE", CalendarSender.calendarUrl(new HashSet<>(message.resourceIds())));
        return effects().asyncReply(messageTwistReject(result));
    }

    @PostMapping("/cancel/{resourceId}/{reservationId}")
    public Effect<String> cancel(@PathVariable String resourceId, @PathVariable String reservationId, @RequestBody Resources resources) {
        List<String> resourceIds = resources.reservationIds();
        String calendarId = resourceId + "@group.calendar.google.com";
        String calEventId = reservationId;
        var stageGoogle = calendarSender.deleteFromGoogle(calendarId, calEventId);
        var stage = stageGoogle.thenCompose(c -> messageCancelToTwist(c, resourceIds));
        return effects().asyncReply(stage);
    }

    public record Resources(List<String> reservationIds) {}

    public record NotifySearchExhausted(String reservationId, String facilityId, Reservation reservation, List<String> resourceIds) {}

    public record Fulfilled(String resourceId, String reservationId, Reservation reservation, List<String> resourceIds, String facilityId) {}
}