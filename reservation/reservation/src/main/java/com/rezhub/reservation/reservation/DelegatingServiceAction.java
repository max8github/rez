package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.spi.CalendarSender;
import com.rezhub.reservation.spi.NotificationSender;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component(id = "delegating-service-consumer")
@Consume.FromEventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
@SuppressWarnings("unused")
public class DelegatingServiceAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);

    final private HttpClient httpClient;

    final private CalendarSender calendarSender;
    final private NotificationSender notificationSender;

    @SuppressWarnings("unused")
    public DelegatingServiceAction(HttpClientProvider httpClientProvider, CalendarSender calendarSender, NotificationSender notificationSender) {
        this.httpClient = httpClientProvider.httpClientFor("twist");
        this.calendarSender = calendarSender;
        this.notificationSender = notificationSender;
    }

    CompletableFuture<String> messageTwistAccept(CalendarSender.ReservationResult result) {
        log.info("called messageTwist for reservation id {}", result.eventDetails().reservationId());
        java.util.List<String> attendees = result.eventDetails().emails();
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
        return notificationSender.messageTwist(httpClient, body);
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
        return notificationSender.messageTwist(httpClient, body);
    }

    /**
     * This is the incoming webhook: Akka -> Twist.
     * It is used for posting a confirmation to Twist that something happened.
     */
    CompletableFuture<String> messageCancelToTwist(CalendarSender.CalendarEventDeletionResult result,
                                                   Set<String> resourceIds) {
        log.info("Messaging Twist confirming cancellation of reservation id {} from calendar {}",
                result.calEventId(), result.calendarId());
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
        return notificationSender.messageTwist(httpClient, body);
    }

    public Effect on(ReservationEvent.Fulfilled event) throws Exception {
        Reservation reservationDto = event.reservation();
        String reservationId = event.reservationId();
        Set<String> resourceIds = event.resourceIds();
        // todo: here i need the resource and facility details, not their ids:
        String resourceId = event.resourceId();
        var eventDetails = new CalendarSender.EventDetails(resourceId, reservationId, "facilityId", resourceIds,
                reservationDto.emails(), reservationDto.dateTime());
        var stageGoogle = calendarSender.saveToGoogle(eventDetails);
        stageGoogle.thenCompose(this::messageTwistAccept)
            .whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Error messaging Twist: {}", error.getMessage());
                }
            });
        return effects().done();
    }

    public Effect on(ReservationEvent.SearchExhausted event) {
        var eventDetails = new CalendarSender.EventDetails("", event.reservationId(), "facilityId",
                event.resourceIds(),
                event.reservation().emails(), event.reservation().dateTime());
        var result = new CalendarSender.ReservationResult(eventDetails, "UNAVAILABLE", CalendarSender.calendarUrl(event.resourceIds()));
        messageTwistReject(result)
            .whenComplete((res, error) -> {
                if (error != null) {
                    log.error("Error messaging Twist: {}", error.getMessage());
                }
            });
        return effects().done();
    }

    public Effect on(ReservationEvent.ReservationCancelled event) throws IOException {
        String calendarId = CalendarSender.calendarIdForResource(event.resourceId());
        String calEventId = event.reservationId();
        var stageGoogle = calendarSender.deleteFromGoogle(calendarId, calEventId);
        stageGoogle.thenCompose(c -> messageCancelToTwist(c, event.resourceIds()))
            .whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Error messaging Twist: {}", error.getMessage());
                }
            });
        return effects().done();
    }
}
