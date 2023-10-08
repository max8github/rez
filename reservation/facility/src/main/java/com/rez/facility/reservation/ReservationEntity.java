package com.rez.facility.reservation;

import com.rez.facility.dto.Reservation;
import kalix.javasdk.annotations.*;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.rez.facility.reservation.ReservationState.State.*;

@Id("reservationId")
@TypeId("reservation")
@RequestMapping("/reservation/{reservationId}")
public class ReservationEntity extends EventSourcedEntity<ReservationState, ReservationEvent> {
    private static final Logger log = LoggerFactory.getLogger(ReservationEntity.class);

    private final String entityId;

    public ReservationEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public static Reservation fromReservationState(ReservationState reservationState) {
        return new Reservation(reservationState.emails(), reservationState.dateTime());
    }

    @Override
    public ReservationState emptyState() {
        return ReservationState.initiate(entityId);
    }

    @PostMapping("/init")
    public Effect<String> init(@RequestBody InitiateReservation command) {
        log.info("ReservationEntity initializes with reservation id {}", entityId);
        return switch (currentState().state()) {
            case CANCELLED -> effects().reply("Reservation cancelled: cannot be initialized");
            case UNAVAILABLE -> effects().reply("Reservation was rejected for unavailable resources: cannot be initialized");
            case FULFILLED -> effects().reply("Reservation was accepted: cannot be reinitialized");
            case COLLECTING, SELECTING -> effects().reply("Reservation is processing resources: cannot be initialized");
            case INIT -> effects()
                    .emitEvent(new ReservationEvent.ReservationInitiated(entityId,
                            command.facilityId(), command.reservation(), command.resources()))
                    .thenReply(newState -> entityId);
        };
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState initiated(ReservationEvent.ReservationInitiated event) {
        Reservation reservation = event.reservation();
        return ReservationState.initiate(event.reservationId()).withState(COLLECTING).withFacilityId(event.facilityId())
          .withEmails(reservation.emails()).withResources(event.resources()).withDateTime(reservation.dateTime());
    }

    @PostMapping("/runSearch")
    public Effect<String> runSearch(@RequestBody RunSearch command) {
        switch (currentState().state()) {
            case COLLECTING -> {
                log.info("Reservation " + entityId + ", in COLLECTING, got " + (command.available() ? "yes " : "no ") + "from resource " + command.resourceId);
                String reservationId = currentState().reservationId();
                Reservation reservation = new Reservation(currentState().emails(), currentState().dateTime());
                if (command.available()) {
                    return effects()
                            .emitEvent(new ReservationEvent.ResourceSelected(command.resourceId(), reservationId, reservation, command.facilityId))
                            .thenReply(newState -> "OK");
                } else {
                    return effects()
                            .emitEvent(new ReservationEvent.ResourceResponded(command.resourceId(), reservationId,
                                    reservation, false, command.facilityId))
                            .thenReply(newState -> "OK");
                }
            }
            case SELECTING -> {
                log.info("Reservation " + entityId + ", in SELECTING, got " + (command.available() ? "yes " : "no ") + "from resource " + command.resourceId);
                String reservationId = currentState().reservationId();
                Reservation reservation = new Reservation(currentState().emails(), currentState().dateTime());
                    return effects()
                            .emitEvent(new ReservationEvent.ResourceResponded(command.resourceId(), reservationId,
                                    reservation, command.available(), command.facilityId))
                            .thenReply(newState -> "OK");
            }
            case INIT, FULFILLED, CANCELLED, UNAVAILABLE -> {
                return effects().error("Reservation " + entityId + " cannot be invoked");
            }
            default -> {
                return effects().error("This should never happen for reservation entity " + entityId);
            }

        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState resourceResponded(ReservationEvent.ResourceResponded event) {
        return event.available()
                ? currentState().withAdded(event.resourceId())
                : currentState().withRemoved(event.resourceId());
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState resourceSelected(ReservationEvent.ResourceSelected event) {
        return currentState().withAdded(event.resourceId()).withState(SELECTING);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState searchExhausted(ReservationEvent.SearchExhausted event) {
        log.info("Search exhausted for reservation {}: UNAVAILABLE ", event.reservationId());
        return currentState().withState(UNAVAILABLE);
    }

    @PostMapping("/book")
    public Effect<String> book(@RequestBody Book command) {
        log.info("Reservation {} gets confirmation from resource {}", entityId, command.resourceId());
        return switch (currentState().state()) {
            case SELECTING -> effects()
                    .emitEvent(new ReservationEvent.Booked(command.resourceId(),
                            entityId, command.reservation(), currentState().resources(), command.facilityId()))
                    .thenReply(newState -> "OK, picked resource " + command.resourceId());
            case INIT, COLLECTING, FULFILLED, CANCELLED, UNAVAILABLE -> effects().reply("Resource cannot be booked");
        };

    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState booked(ReservationEvent.Booked event) {
        log.info("Reservation {} FULFILLED with resource {}", event.reservationId(), event.resourceId());
        return currentState().withRemoved(event.resourceId()).withResourceId(event.resourceId()).withState(FULFILLED);
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<ReservationState> getReservation() {
        return effects().reply(currentState());
    }

    @DeleteMapping("/cancelRequest")
    public Effect<String> cancelRequest() {
        log.info("Cancelling reservation {} requested", entityId);
        return switch (currentState().state()) {
            case FULFILLED, COLLECTING -> {
                String resourceId = getResourceIdFromState();
                yield effects()
                        .emitEvent(new ReservationEvent.CancelRequested(entityId,
                                currentState().facilityId(), resourceId, currentState().dateTime()))
                        .thenReply(newState -> entityId);
            }
            default ->
                    effects().error("Reservation entity " + entityId + " must be in fulfilled state to be cancelled");
        };
    }

    private String getResourceIdFromState() {
        ReservationState state = currentState();
        return state.resourceId();
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState cancelRequested(ReservationEvent.CancelRequested event) {
        return currentState();
    }

    @DeleteMapping("/cancel")
    public Effect<String> cancel() {
        log.info("Cancelling of reservation {} is confirmed", entityId);
        switch (currentState().state()) {//todo: states here ok in the FSM workings?
            case FULFILLED, COLLECTING -> {
                String resourceId = getResourceIdFromState();
                return effects()
                        .emitEvent(new ReservationEvent.ReservationCancelled(
                                entityId,
                                currentState().facilityId(),
                                fromReservationState(currentState()),
                                resourceId, currentState().resources()))
                        .thenReply(newState -> entityId);
            }
            default -> {
                return effects().error("reservation entity " + entityId + " was not in fulfilled state");
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState reservationCancelled(ReservationEvent.ReservationCancelled event) {
        log.info("Reservation {} cancelled from resource {}", event.reservationId(), event.resourceId());
        return currentState().withState(CANCELLED);
    }

    @PostMapping("/expire")
    public Effect<String> expire() {
        log.info("Reservation {} is asked to expire for timeout", entityId);
        return switch (currentState().state()) {
            case INIT, COLLECTING, SELECTING -> effects()
                        .emitEvent(new ReservationEvent.SearchExhausted(
                                entityId,
                                currentState().facilityId(),
                                new Reservation(currentState().emails(), currentState().dateTime()),
                                currentState().resources()))
                        .thenReply(newState -> entityId);
            case FULFILLED, CANCELLED, UNAVAILABLE -> effects().reply("OK");
        };
    }

    @PostMapping("/reject")
    public Effect<String> reject(@RequestBody RunSearch command) {

        switch (currentState().state()) {
            case SELECTING -> {
                String resourceId = command.resourceId();
                log.info("Reservation {} was rejected (resource {}) and it has to find another resource", entityId, resourceId);
                return effects().emitEvent(new ReservationEvent.Waiting(entityId, resourceId))
                            .thenReply(newState -> "OK");
            }
            default -> {
                return effects().error("Reservation " + entityId + " is completed");
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState waiting(ReservationEvent.Waiting event) {
        log.info("Waiting for availability");
        return currentState().withRemoved(event.resourceId()).withState(COLLECTING);
    }

    @PostMapping("/tryNext")
    public Effect<String> tryNext(@RequestBody Wait command) {

        switch (currentState().state()) {
            case COLLECTING -> {
                log.info("Reservation {} must find another resource, other than {}", entityId, command.resourceId);
                log.info("Reservation {} has available resources: {}", entityId, currentState().hasAvailableResources());
                log.info("Reservation {} resources: {}", entityId, currentState().resources());
                String reservationId = currentState().reservationId();
                String facilityId = currentState().facilityId();

                if(currentState().hasAvailableResources()) {
                    String resourceId = currentState().pop();
                    Reservation reservation = new Reservation(currentState().emails(), currentState().dateTime());
                    return effects()
                            .emitEvent(new ReservationEvent.ResourceSelected(resourceId, reservationId, reservation, facilityId))
                            .thenReply(newState -> "OK");
                } else {
                    return effects().emitEvent(new ReservationEvent.KeepWaiting(reservationId))
                            .thenReply(newState -> "OK");
                }
            }
            default -> {
                log.info("Reservation id {}, state {} is completed, resources: {}", entityId, currentState().state(), currentState().resources());
                return effects().error("Reservation '" + entityId + "' is " + currentState().state());
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ReservationState waiting(ReservationEvent.KeepWaiting event) {
        log.info("Keep waiting for availability");
        return currentState().withState(COLLECTING);
    }

    public record InitiateReservation(String facilityId, Reservation reservation,
                                      List<String> resources) {}

    public record RunSearch(String reservationId, String resourceId, boolean available, String facilityId) {}
    public record Wait(String reservationId, String resourceId) {}

    public record Book(String resourceId, String reservationId, Reservation reservation, String facilityId) {}
}