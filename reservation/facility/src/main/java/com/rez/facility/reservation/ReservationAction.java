package com.rez.facility.reservation;

import com.google.protobuf.any.Any;
import com.rez.facility.resource.ResourceEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = ReservationEntity.class, ignoreUnknown = true)
public class ReservationAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(ReservationAction.class);
    private final ComponentClient kalixClient;

    public ReservationAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    public Effect<String> on(ReservationEvent.ReservationInitiated event) {
        log.info("Broadcast starts to resources{}", event.resources());
        List<CompletableFuture<String>> futureList = event.resources().stream().sorted().map(id -> {
            var command = new ResourceEntity.InquireBooking(event.reservationId(), event.facilityId(), event.reservation());
            return kalixClient.forEventSourcedEntity(id).call(ResourceEntity::inquireBooking).params(command).execute().toCompletableFuture();
        }).toList();

        CompletableFuture<Effect<String>> completableFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> futureList.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                ).thenApply(v -> effects().reply("ok - broadcast"));

        return effects().asyncEffect(completableFuture);
    }

    public Effect<String> on(ReservationEvent.ResourceSelected event) {
        log.info("Reservation {} has a candidate ({}) and sends a BookIt to it", event.reservationId(), event.resourceId());
        var resourceId = event.resourceId();
        var command = new ResourceEntity.BookIt(event.reservationId(), event.reservation(), event.facilityId());
        DeferredCall<Any, String> deferredCall = kalixClient.forEventSourcedEntity(resourceId).call(ResourceEntity::bookIt).params(command);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.Waiting event) {
        var reservationId = event.reservationId();
        var resourceId = event.resourceId();
        log.info("Reservation {} had a candidate ({}), but that refused, after attempting booking it. Now back to waiting.", reservationId, resourceId);
        var command = new ReservationEntity.Wait(reservationId, resourceId);
        DeferredCall<Any, String> deferredCall = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::tryNext).params(command);
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
