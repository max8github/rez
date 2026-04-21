package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.ResourceV;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.spi.NotificationSender;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

// TODO: notification responsibilities belong in a dedicated Booking Orchestration service.
//       Move NotificationSender calls out once that service is extracted from this module.
@Component(id = "delegating-service-consumer")
@Consume.FromEventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
@SuppressWarnings("unused")
public class DelegatingServiceAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);

    private final NotificationSender notificationSender;
    private final ComponentClient componentClient;

    public DelegatingServiceAction(NotificationSender notificationSender, ComponentClient componentClient) {
        this.notificationSender = notificationSender;
        this.componentClient = componentClient;
    }

    public Effect on(ReservationEvent.Fulfilled event) {
        Reservation reservation = event.reservation();
        String reservationId = event.reservationId();
        String resourceId = event.resourceId();
        String recipientId = event.recipientId();

        Optional<ResourceV> resourceOpt = componentClient.forView()
            .method(ResourceView::getResourceById)
            .invoke(resourceId);
        String courtLabel = resourceOpt.map(ResourceV::resourceName).orElse(resourceId);

        String attendees = String.join(", ", reservation.emails());
        String text = ("\uD83C\uDFBE %s\n\uD83D\uDCC5 %s\n\uD83D\uDC65 %s\n\n\uD83C\uDD94 <code>%s</code>")
            .formatted(courtLabel, reservation.dateTime(), attendees, reservationId);
        notificationSender.send(recipientId, text)
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
        String text = "Reservation %s has been cancelled.".formatted(event.reservationId());
        notificationSender.send(recipientId, text)
            .whenComplete((result, error) -> {
                if (error != null) log.error("Error sending cancellation notification: {}", error.getMessage());
            });
        return effects().done();
    }
}
