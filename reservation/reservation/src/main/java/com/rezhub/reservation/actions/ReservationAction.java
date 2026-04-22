package com.rezhub.reservation.actions;

import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationEvent;
import com.rezhub.reservation.resource.ResourceEntity;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
@Component(id = "reservation-events-consumer")
@Consume.FromEventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class ReservationAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(ReservationAction.class);
    private final ComponentClient componentClient;

    public ReservationAction(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect on(ReservationEvent.Inited event) {
        log.info("Fanning out checkAvailability for reservation {} to {} resources",
            event.reservationId(), event.resourceIds().size());
        event.resourceIds().forEach(resourceId -> {
            var command = new ResourceEntity.CheckAvailability(event.reservationId(), event.reservation());
            componentClient.forEventSourcedEntity(resourceId)
                .method(ResourceEntity::checkAvailability)
                .invokeAsync(command)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Error checking availability for resource {}: {}", resourceId, error.getMessage());
                    }
                });
        });
        return effects().done();
    }

    public Effect on(ReservationEvent.ResourceSelected event) {
        log.info("Reservation {} has a candidate ({}) and sends a Fulfill to it", event.reservationId(), event.resourceId());
        var resourceId = event.resourceId();
        var command = new ResourceEntity.Reserve(event.reservationId(), event.reservation());
        componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::reserve)
            .invoke(command);
        return effects().done();
    }

    public Effect on(ReservationEvent.CancelRequested event) {
        log.info("Cancel reservation {} in resource {}", event.reservationId(), event.resourceId());
        var resourceId = event.resourceId();
        var command = new ResourceEntity.CancelReservation(event.reservationId(), event.dateTime());
        componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::cancel)
            .invoke(command);
        return effects().done();
    }
}
