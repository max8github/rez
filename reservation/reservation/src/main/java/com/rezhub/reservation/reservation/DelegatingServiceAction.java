package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.spi.CalendarSender;
import com.rezhub.reservation.spi.NotificationSender;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Component(id = "delegating-service-consumer")
@Consume.FromEventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
@SuppressWarnings("unused")
public class DelegatingServiceAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);

    private final CalendarSender calendarSender;
    private final NotificationSender notificationSender;

    public DelegatingServiceAction(CalendarSender calendarSender, NotificationSender notificationSender) {
        this.calendarSender = calendarSender;
        this.notificationSender = notificationSender;
    }

    public Effect on(ReservationEvent.Fulfilled event) throws Exception {
        Reservation reservationDto = event.reservation();
        String reservationId = event.reservationId();
        Set<String> resourceIds = event.resourceIds();
        String resourceId = event.resourceId();
        String recipientId = event.recipientId();

        var eventDetails = new CalendarSender.EventDetails(resourceId, reservationId, "facilityId", resourceIds,
                reservationDto.emails(), reservationDto.dateTime());
        calendarSender.saveToGoogle(eventDetails)
            .thenCompose(result -> {
                String attendees = String.join(", ", reservationDto.emails());
                String text = "Reservation confirmed! ID: `%s`. Date/Time: %s. Players: %s. Calendar: %s"
                    .formatted(reservationId, reservationDto.dateTime(), attendees, result.url());
                return notificationSender.send(recipientId, text);
            })
            .whenComplete((result, error) -> {
                if (error != null) log.error("Error sending booking confirmation: {}", error.getMessage());
            });
        return effects().done();
    }

    public Effect on(ReservationEvent.SearchExhausted event) {
        String recipientId = event.recipientId();
        String time = event.reservation().dateTime().toString();
        String text = "Sorry, no court was available for %s. Please try a different time.".formatted(time);
        notificationSender.send(recipientId, text)
            .whenComplete((res, error) -> {
                if (error != null) log.error("Error sending unavailable notification: {}", error.getMessage());
            });
        return effects().done();
    }

    public Effect on(ReservationEvent.ReservationCancelled event) {
        String recipientId = event.recipientId();
        String calendarId = CalendarSender.calendarIdForResource(event.resourceId());
        calendarSender.deleteFromGoogle(calendarId, event.reservationId())
            .thenCompose(result -> {
                String text = "Reservation %s has been cancelled.".formatted(event.reservationId());
                return notificationSender.send(recipientId, text);
            })
            .whenComplete((result, error) -> {
                if (error != null) log.error("Error sending cancellation notification: {}", error.getMessage());
            });
        return effects().done();
    }
}
