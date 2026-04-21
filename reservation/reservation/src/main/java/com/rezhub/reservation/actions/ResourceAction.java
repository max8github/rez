package com.rezhub.reservation.actions;

import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceEvent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
@Component(id = "resource-events-consumer")
@Consume.FromEventSourcedEntity(value = ResourceEntity.class, ignoreUnknown = true)
public class ResourceAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(ResourceAction.class);

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public ResourceAction(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    @SuppressWarnings("unused")
    public Effect on(ResourceEvent.FacilityResourceCreated event) {
        log.debug("Facility Asset was Created with id {}", event.resourceId());
        componentClient
            .forEventSourcedEntity(event.parentId())
            .method(FacilityEntity::registerResource)
            .invoke(event.resourceId());
        return effects().done();
    }

    @SuppressWarnings("unused")
    public Effect on(ResourceEvent.AvalabilityChecked event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.ReplyAvailability(reservationId, event.resourceId(), event.available());
        componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::replyAvailability)
            .invoke(command);
        return effects().done();
    }

    @SuppressWarnings("unused")
    public Effect on(ResourceEvent.ReservationAccepted event) {
        var resourceId = event.resourceId();
        String reservationId = event.reservationId();
        log.info("Resource {} sends acceptance to reservation {}", resourceId, reservationId);
        var command = new ReservationEntity.Fulfill(event.resourceId(), reservationId, event.reservation());

        componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::fulfill)
            .invokeAsync(command)
            .thenAccept(result -> {
                if (result.startsWith("OK")) {
                    timerScheduler.delete(TimerAction.timerName(reservationId));
                } else {
                    // Reservation could not fulfill (e.g. expired while resource was locking).
                    // The resource lock was already persisted — compensate by releasing it.
                    log.warn("Reservation {} rejected fulfill from resource {} — compensating lock release",
                        reservationId, resourceId);
                    componentClient
                        .forEventSourcedEntity(resourceId)
                        .method(ResourceEntity::cancel)
                        .invoke(new ResourceEntity.CancelReservation(reservationId, event.reservation().dateTime()));
                }
            });

        return effects().done();
    }

    @SuppressWarnings("unused")
    public Effect on(ResourceEvent.ReservationRejected event) {
        log.info("Resource {} sends rejection to reservation {}, tryNext", event.resourceId(), event.reservationId());
        var reservationId = event.reservationId();
        var command = new ReservationEntity.Reject(event.resourceId());
        componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::reject)
            .invoke(command);
        return effects().done();
    }

    @SuppressWarnings("unused")
    public Effect on(ResourceEvent.ReservationCanceled event) {
        var reservationId = event.reservationId();
        componentClient
            .forEventSourcedEntity(reservationId)
            .method(ReservationEntity::cancel)
            .invoke();
        return effects().done();
    }
}
