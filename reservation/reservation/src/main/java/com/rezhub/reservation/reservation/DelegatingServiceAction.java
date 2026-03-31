package com.rezhub.reservation.reservation;

import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.ResourceV;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.spi.CalendarSender;
import com.rezhub.reservation.spi.NotificationSender;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Component(id = "delegating-service-consumer")
@Consume.FromEventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
@SuppressWarnings("unused")
public class DelegatingServiceAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);

    private final CalendarSender calendarSender;
    private final NotificationSender notificationSender;
    private final ComponentClient componentClient;

    public DelegatingServiceAction(CalendarSender calendarSender, NotificationSender notificationSender,
                                   ComponentClient componentClient) {
        this.calendarSender = calendarSender;
        this.notificationSender = notificationSender;
        this.componentClient = componentClient;
    }

    public Effect on(ReservationEvent.Fulfilled event) throws Exception {
        Reservation reservationDto = event.reservation();
        String reservationId = event.reservationId();
        String resourceId = event.resourceId();
        String recipientId = event.recipientId();

        Optional<ResourceV> resourceOpt = componentClient.forView()
            .method(ResourceView::getResourceById)
            .invoke(resourceId);

        String calendarId = resourceOpt.map(ResourceV::calendarId).orElse(resourceId);
        String facilityId = resourceOpt.map(ResourceV::facilityId).orElse(null);

        List<String> facilityCalendarIds = List.of();
        String timezone = "Europe/Berlin";
        String facilityAddress = "";
        if (facilityId != null) {
            ResourceView.Resources facilityResources = componentClient.forView()
                .method(ResourceView::getResource)
                .invoke(facilityId);
            facilityCalendarIds = facilityResources.resources().stream()
                .map(ResourceV::calendarId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

            Facility facility = componentClient.forEventSourcedEntity(facilityId)
                .method(FacilityEntity::getFacility)
                .invoke();
            if (facility.timezone() != null) timezone = facility.timezone();
            if (facility.address() != null) {
                facilityAddress = facility.address().street() + ", " + facility.address().city();
            }
        }

        String calUrl = CalendarSender.calendarUrlFromIds(facilityCalendarIds);
        String courtLabel = resourceOpt.map(ResourceV::resourceName).orElse(resourceId);

        var eventDetails = new CalendarSender.EventDetails(resourceId, courtLabel, reservationId, calendarId, timezone,
                event.resourceIds(), reservationDto.emails(), reservationDto.dateTime(), facilityAddress);
        calendarSender.saveToGoogle(eventDetails)
            .thenCompose(result -> {
                String attendees = String.join(", ", reservationDto.emails());
                String text = ("🎾 %s\n"
                    + "📅 %s\n"
                    + "👥 %s\n\n"
                    + "🆔 <code>%s</code>\n\n"
                    + "<a href=\"%s\">📆 Calendar</a>")
                    .formatted(courtLabel, reservationDto.dateTime(), attendees, reservationId, calUrl);
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

        Optional<ResourceV> resourceOpt = componentClient.forView()
            .method(ResourceView::getResourceById)
            .invoke(event.resourceId());
        String calendarId = resourceOpt.map(ResourceV::calendarId).orElse(event.resourceId());

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
