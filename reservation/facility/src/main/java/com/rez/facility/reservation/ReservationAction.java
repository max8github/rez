package com.rez.facility.reservation;

import com.rez.facility.resource.ResourceEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class ReservationAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(ReservationAction.class);
    private final ComponentClient kalixClient;

    public ReservationAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(ReservationEvent.ReservationInitiated event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.RunSearch(reservationId, event.facilityId(), event.reservationDto());
        var deferredCall = kalixClient.forEventSourcedEntity(reservationId)
                .call(ReservationEntity::runSearch).params(command);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.ResourceSelected event) {
        var resourceId = event.resourceId();
        var command = new ResourceEntity.InquireBooking(resourceId, event.reservationId(), event.facilityId(), event.reservationDto());
        var deferredCall = kalixClient.forEventSourcedEntity(resourceId).call(ResourceEntity::inquireBooking).params(command);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.CancelRequested event) {
        log.info("Cancel reservation {} in resource {}", event.reservationId(), event.resourceId());
        var resourceId = event.resourceId();
        var deferredCall = kalixClient.forEventSourcedEntity(resourceId).call(ResourceEntity::cancel)
                .params(event.reservationId(), event.dateTime().toString());
        return effects().forward(deferredCall);
    }
}
