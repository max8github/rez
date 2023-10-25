package com.rezhub.reservation.actions;

import com.google.protobuf.any.Any;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationEvent;
import com.rezhub.reservation.resource.ResourceEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Set;
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

    public Effect<String> on(ReservationEvent.Inited event) {
        log.info("Broadcast starts to selection {}", event.selection());
        CompletableFuture<Effect<String>> completableFuture = broadcast(this, kalixClient, event.reservationId(),
          event.reservation(), event.selection());
        return effects().asyncEffect(completableFuture);
    }

    public static CompletableFuture<Effect<String>> broadcast(Action action, ComponentClient kalixClient,
                                                       String reservationId, Reservation reservation,
                                                       Set<String> resources) {
        List<CompletableFuture<String>> futureChecks = resources.stream().sorted().map(id -> {
            var command = new ResourceEntity.CheckAvailability(reservationId, reservation);
            //Note: cannot use inheritance. If it were possible, checkAvailability() would
            //be a method (of a super entity) with polymorphic behavior.
            if(id.startsWith("pool")) {
                return kalixClient.forEventSourcedEntity(id).call(FacilityEntity::checkAvailability).params(command).execute().toCompletableFuture();
            } else {
                return kalixClient.forEventSourcedEntity(id).call(ResourceEntity::checkAvailability).params(command).execute().toCompletableFuture();
            }
        }).toList();

      return CompletableFuture.allOf(futureChecks.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> futureChecks.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                ).thenApply(v -> action.effects().reply("ok - broadcast"));
    }

    public Effect<String> on(ReservationEvent.ResourceSelected event) {
        log.info("Reservation {} has a candidate ({}) and sends a Fulfill to it", event.reservationId(), event.resourceId());
        var resourceId = event.resourceId();
        var command = new ResourceEntity.Reserve(event.reservationId(), event.reservation());
        DeferredCall<Any, String> deferredCall = kalixClient.forEventSourcedEntity(resourceId).call(ResourceEntity::reserve).params(command);
        return effects().forward(deferredCall);
    }

    public Effect<String> on(ReservationEvent.RejectedWithNext event) {
        var reservationId = event.reservationId();
        var resourceId = event.resourceId();
        var nextResourceId = event.nextResourceId();
        log.info("Reservation {} had a candidate ({}), but that got subsequently rejected. Now to try: {}.",
          reservationId, resourceId, nextResourceId);
        var command = new ReservationEntity.ReplyAvailability(reservationId, event.nextResourceId(), true);
        DeferredCall<Any, String> deferredCall = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::replyAvailability).params(command);
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
