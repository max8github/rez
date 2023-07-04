package com.rez.facility.api;

import com.rez.facility.domain.ReservationState;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.rez.facility.domain.ReservationState.State.*;

@EntityKey("reservationId")
@EntityType("reservation")
@RequestMapping("/reservation/{reservationId}")
public class ReservationEntity extends EventSourcedEntity<ReservationState, ReservationEvent> {
    private static final Logger log = LoggerFactory.getLogger(ReservationEntity.class);

    private final String entityId;

    public ReservationEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public ReservationState emptyState() {
        return ReservationState.initiate(entityId);
    }

    @PostMapping("/init")
    public Effect<String> init(@RequestBody InitiateReservation command) {
        log.info("Created reservation {} entity", command.reservationId);
        switch (currentState().state()) {
//            case UNAVAILABLE:
            case INIT:
                    return effects()
                            .emitEvent(new ReservationEvent.ReservationInitiated(command.reservationId(),
                                    command.facilityId(), command.reservation(), command.resources()))
                            .thenReply(newState -> command.reservationId());
            default:
                return effects().error("reservation entity " + command.reservationId() + " already initiated");
        }
    }

    @EventHandler
    public ReservationState initiated(ReservationEvent.ReservationInitiated event) {
        return event.reservation()
                .toReservationState(event.reservationId(), event.facilityId(), event.resources())
                .withState(SELECTING);
    }

    @PostMapping("/runSearch")
    public Effect<String> runSearch(@RequestBody RunSearch command) {
        switch (currentState().state()) {
            case INIT:
            case SELECTING:
                var nextIndex = currentState().currentResourceIndex() + 1;
                if(currentState().resources().size() > nextIndex) {
                    var nextResourceId = currentState().resources().get(nextIndex);
                    log.info("Reservation {} searching for availability: resource {}", command.reservationId, nextResourceId);
                    return effects()
                            .emitEvent(new ReservationEvent.ResourceSelected(nextIndex, nextResourceId,
                                    command.reservationId(), command.facilityId(), command.reservation))
                            .thenReply(newState -> "OK");
                } else {
                    return effects()
                            .emitEvent(new ReservationEvent.SearchExhausted(command.reservationId(),
                                    command.facilityId(), command.reservation(), currentState().resources()))
                            .thenReply(newState -> "Not Available");
                }
            case FULFILLED:
            case UNAVAILABLE:
                return effects().error("Reservation " + command.reservationId()
                        + "is completed for facility id " + command.facilityId());
            default:
                return effects().error("This should never happen for reservation entity " + command.reservationId()
                        + "facility id " + command.facilityId());

        }
    }

    @EventHandler
    public ReservationState resourceSelected(ReservationEvent.ResourceSelected event) {
        return currentState().withIncrementedIndex().withState(SELECTING);
    }

    @EventHandler
    public ReservationState searchExhausted(ReservationEvent.SearchExhausted event) {
        log.info("Search exhausted for reservation {}: UNAVAILABLE ", event.reservationId());
        return currentState().withIncrementedIndex().withState(UNAVAILABLE);
    }

    @PostMapping("/book")
    public Effect<String> book(@RequestBody Book command) {
            return effects()
                    .emitEvent(new ReservationEvent.Booked(command.resourceId(),
                            command.reservationId(), command.reservation(), currentState().resources()))
                    .thenReply(newState -> "OK, picked resource " + command.resourceId());
    }

    @EventHandler
    public ReservationState booked(ReservationEvent.Booked event) {
        log.info("Reservation {} booked in resource {}", event.reservationId(), event.resourceId());
        return currentState().withIncrementedIndex().withState(FULFILLED);
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<ReservationState> getReservation() {
        return effects().reply(currentState());
    }

    @DeleteMapping("/cancelRequest")
    public Effect<String> cancelRequest() {
        log.info("Cancelling reservation {} requested", entityId);
        switch (currentState().state()) {
            case FULFILLED:
                int i = currentState().currentResourceIndex();
                String resourceId = currentState().resources().get(i);
                return effects()
                        .emitEvent(new ReservationEvent.CancelRequested(entityId,
                                currentState().facilityId(), resourceId, currentState().timeSlot()))
                        .thenReply(newState -> entityId);
            default:
                return effects().error("reservation entity " + entityId + " was not in fulfilled state");
        }
    }

    @EventHandler
    public ReservationState cancelRequested(ReservationEvent.CancelRequested event) {
        return currentState();
    }

    @DeleteMapping("/cancel")
    public Effect<String> cancel() {
        log.info("Cancelling of reservation {} is confirmed", entityId);
        switch (currentState().state()) {//todo: states here ok in the FSM workings?
            case FULFILLED:
                int i = currentState().currentResourceIndex();
                String resourceId = currentState().resources().get(i);
                return effects()
                        .emitEvent(new ReservationEvent.ReservationCancelled(
                                entityId,
                                currentState().facilityId(),
                                Mod.Reservation.fromReservationState(currentState()),
                                resourceId))
                        .thenReply(newState -> entityId);
            default:
                return effects().error("reservation entity " + entityId + " was not in fulfilled state");
        }
    }

    @EventHandler
    public ReservationState reservationCancelled(ReservationEvent.ReservationCancelled event) {
        log.info("Reservation {} cancelled from resource {}", event.reservationId(), event.resourceId());
        return currentState().withIncrementedIndex().withState(CANCELLED);
    }

    public record InitiateReservation(String reservationId, String facilityId, Mod.Reservation reservation,
                                      List<String> resources) {}

    public record RunSearch(String reservationId, String facilityId, Mod.Reservation reservation) {}

    public record Book(String resourceId, String reservationId, Mod.Reservation reservation) {}

}