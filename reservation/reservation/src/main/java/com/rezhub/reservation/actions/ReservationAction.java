package com.rezhub.reservation.actions;

import com.rezhub.reservation.customer.facility.FacilityAction;
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
@Consume.FromEventSourcedEntity(ReservationEntity.class)
public class ReservationAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(ReservationAction.class);
    private final ComponentClient componentClient;

    public ReservationAction(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect on(ReservationEvent.Inited event) {
        log.info("Broadcast starts to selection {}", event.selection());
        FacilityAction.broadcast(componentClient, event.reservationId(),
          event.reservation(), event.selection());
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

    public Effect on(ReservationEvent.RejectedWithNext event) {
        var reservationId = event.reservationId();
        var resourceId = event.resourceId();
        var nextResourceId = event.nextResourceId();
        log.info("Reservation {} had a candidate ({}), but that got subsequently rejected. Now to try: {}.",
          reservationId, resourceId, nextResourceId);
        var command = new ReservationEntity.ReplyAvailability(reservationId, event.nextResourceId(), true);
        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::replyAvailability)
            .invoke(command);
        return effects().done();
    }

    public Effect on(ReservationEvent.CancelRequested event) {
        log.info("Cancel reservation {} in resource {}", event.reservationId(), event.resourceId());
        var resourceId = event.resourceId();
        componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::cancel)
            .invoke(event.reservationId(), event.dateTime().toString());
        return effects().done();
    }
}
