package com.rezhub.reservation.actions;

import akka.Done;
import com.rezhub.reservation.pool.FacilityEntity;
import com.rezhub.reservation.pool.FacilityEvent;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(FacilityAction.class);
    public static final int TIMEOUT = 5;
    private final ComponentClient kalixClient;

    public FacilityAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    @SuppressWarnings("unused")
    public Effect<String> on(FacilityEvent.ResourceSubmitted event) {
        var resourceEntityId = event.resourceId();
        var command = new ResourceEntity.CreateResourceCommand(event.facilityId(), event.resourceDto());
        var deferredCall = kalixClient.forEventSourcedEntity(resourceEntityId).call(ResourceEntity::create).params(command);
        return effects().forward(deferredCall);
    }

    @SuppressWarnings("unused")
    public Effect<String> on(FacilityEvent.ReservationCreated event) {
        var reservationId = event.reservationId();
        var command = new ReservationEntity.Init(event.facilityId(), event.reservationDto(), event.resources());
        CompletionStage<Done> timerRegistration =
          timers().startSingleTimer(
            timerName(event.reservationId()),
            Duration.ofSeconds(TIMEOUT),
            kalixClient.forAction().call(TimerAction::expire).params(event.reservationId())
          );
        var request = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::init).params(command);
        return effects().asyncReply(
          timerRegistration
            .thenCompose(done -> request.execute())
            .thenApply(reservation -> reservation)
        );
    }

    static String timerName(String reservationId) {
        return "timer-" + reservationId;
    }
}
