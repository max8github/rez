package com.rez.facility.actions;

import com.rez.facility.pool.FacilityEntity;
import com.rez.facility.reservation.ReservationEntity;
import com.rez.facility.resource.ResourceEntity;
import com.rez.facility.resource.ResourceEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = ResourceEntity.class, ignoreUnknown = true)
public class ResourceAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(ResourceAction.class);

    private final ComponentClient kalixClient;

    public ResourceAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.ResourceCreated event) {
        var deferredCall = kalixClient.forEventSourcedEntity(event.facilityId())
                .call(FacilityEntity::addResourceId).params(event.resourceId());
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.AvalabilityChecked event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.RunSearch(reservationId, event.resourceId(), event.available(), event.facilityId());
        var deferredCall = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::runSearch).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.BookingAccepted event) {
        var resourceId = event.resourceId();
        String reservationId = event.reservationId();
        log.info("Resource {} sends acceptance to reservation {}", resourceId, reservationId);
        var command = new ReservationEntity.Book(event.resourceId(), reservationId, event.reservationDto(), event.facilityId());

        CompletionStage<String> reply = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::book).params(command)
                .execute()
                .thenCompose(req -> timers().cancel(FacilityAction.timerName(reservationId)))
                .thenApply(done -> "Ok");

        return effects().asyncReply(reply);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.BookingRejected event) {
        log.info("Resource {} sends rejection to reservation {}, tryNext", event.resourceId(), event.reservationId());
        var reservationId = event.reservationId();
        var command = new ReservationEntity.RunSearch(reservationId, event.resourceId(), false, event.facilityId());
        var deferredCall = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::reject).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(ResourceEvent.BookingCanceled event) {
        var reservationId = event.reservationId();
        var deferredCall = kalixClient.forEventSourcedEntity(reservationId)
                .call(ReservationEntity::cancel);
        return effects().forward(deferredCall);
    }
}
