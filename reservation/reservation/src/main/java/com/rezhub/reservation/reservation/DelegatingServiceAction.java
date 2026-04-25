package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.ResourceV;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.spi.NotificationSender;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

// TODO: notification responsibilities belong in a dedicated Booking Orchestration service.
//       Move NotificationSender calls out once that service is extracted from this module.
@Component(id = "delegating-service-consumer")
@Consume.FromEventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
@SuppressWarnings("unused")
public class DelegatingServiceAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(DelegatingServiceAction.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter WEEK_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final NotificationSender notificationSender;
    private final ComponentClient componentClient;
    private final String calendarBaseUrl;

    public DelegatingServiceAction(NotificationSender notificationSender, ComponentClient componentClient, Config config) {
        this.notificationSender = notificationSender;
        this.componentClient = componentClient;
        if (config.hasPath("rez.calendar.base-url")) {
            this.calendarBaseUrl = config.getString("rez.calendar.base-url");
        } else {
            int port = getDevModePort(config);
            this.calendarBaseUrl = "http://localhost:" + port;
        }
    }

    private static int getDevModePort(Config config) {
        try {
            return config.getInt("akka.javasdk.dev-mode.http-port");
        } catch (ConfigException.WrongType e) {
            return Integer.parseInt(config.getString("akka.javasdk.dev-mode.http-port"));
        }
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
        String facilityId = resourceOpt.map(ResourceV::facilityId).orElse("");

        LocalDateTime dt = reservation.dateTime();
        String formattedDate = dt.format(DATE_FMT);
        String weekStart = dt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(WEEK_FMT);
        String calendarUrl = calendarBaseUrl + "/calendar?facilityId=" + facilityId + "&week=" + weekStart;

        String attendees = String.join(", ", reservation.emails());
        String text = ("\uD83C\uDFBE %s\n\uD83D\uDCC5 %s\n\uD83D\uDC65 %s\n\uD83D\uDCC6 %s\n\n\uD83C\uDD94 <code>%s</code>")
            .formatted(courtLabel, formattedDate, attendees, calendarUrl, reservationId);
        notificationSender.send(recipientId, text)
            .whenComplete((result, error) -> {
                if (error != null) log.error("Error sending booking confirmation: {}", error.getMessage());
            });
        return effects().done();
    }

    public Effect on(ReservationEvent.SearchExhausted event) {
        String recipientId = event.recipientId();
        String time = event.reservation().dateTime().format(DATE_FMT);
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
