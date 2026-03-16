package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.rezhub.reservation.reservation.ReservationState.State.*;

/**
 * For an explanation on how this work, see description of 'broadcast' in git commits.
 */
@Component(id = "reservation")
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

    @Override
    public ReservationState applyEvent(ReservationEvent event) {
        return switch (event) {
            case ReservationEvent.Inited e -> {
                Reservation reservation = e.reservation();
                yield ReservationState.initiate(e.reservationId())
                    .withState(COLLECTING)
                    .withSelection(e.selection())
                    .withDateTime(reservation.dateTime())
                    .withEmails(reservation.emails());
            }
            case ReservationEvent.AvailabilityReplied e -> e.available()
                ? currentState().withAdded(e.resourceId())
                : currentState().withRemoved(e.resourceId());
            case ReservationEvent.ResourceSelected e ->
                currentState().withAdded(e.resourceId()).withState(SELECTING);
            case ReservationEvent.SearchExhausted e -> {
                log.info("Search exhausted for reservation {}: UNAVAILABLE ", e.reservationId());
                yield currentState().withState(UNAVAILABLE);
            }
            case ReservationEvent.Fulfilled e -> {
                log.info("Reservation {} FULFILLED with resource {}", e.reservationId(), e.resourceId());
                yield currentState().withRemoved(e.resourceId()).withResourceId(e.resourceId()).withState(FULFILLED);
            }
            case ReservationEvent.CancelRequested e -> currentState();
            case ReservationEvent.ReservationCancelled e -> {
                log.info("Reservation {} cancelled from resource {}", e.reservationId(), e.resourceId());
                yield currentState().withState(CANCELLED);
            }
            case ReservationEvent.RejectedWithNext e -> {
                log.info("Reservation was rejected => next availability");
                yield currentState().withRemoved(e.resourceId()).withState(COLLECTING);
            }
            case ReservationEvent.Rejected e -> {
                log.info("Reservation was rejected, waiting for availability");
                yield currentState().withRemoved(e.resourceId()).withState(COLLECTING);
            }
        };
    }

    public Effect<ReservationId> init(Init command) {
        String id = commandContext().entityId();
        log.info("ReservationEntity initializes with reservation id {}", id);
        return switch (currentState().state()) {
            case CANCELLED -> effects().error("Reservation cancelled: cannot be initialized");
            case UNAVAILABLE -> effects().error("Reservation was rejected for unavailable selection: cannot be initialized");
            case FULFILLED -> effects().error("Reservation had already been accepted: it cannot be reinitialized");
            case COLLECTING, SELECTING -> effects().error("Reservation is processing selection: cannot be initialized");
            case INIT -> effects()
                .persist(new ReservationEvent.Inited(id, command.reservation(), command.selection()))
                .thenReply(newState -> new ReservationId(id));
        };
    }

    public Effect<String> replyAvailability(ReplyAvailability command) {
        switch (currentState().state()) {
            case COLLECTING -> {
                log.info("Reservation " + entityId + ", in COLLECTING, got a " + (command.available() ? "YES " : "NO ") + "from resource " + command.resourceId);
                String reservationId = currentState().reservationId();
                Reservation reservation = new Reservation(currentState().emails(), currentState().dateTime());
                if (command.available()) {
                    return effects()
                        .persist(new ReservationEvent.ResourceSelected(command.resourceId(), reservationId, reservation))
                        .thenReply(newState -> "OK");
                } else {
                    return effects()
                        .persist(new ReservationEvent.AvailabilityReplied(command.resourceId(), reservationId,
                            reservation, false))
                        .thenReply(newState -> "OK");
                }
            }
            case SELECTING, UNAVAILABLE, FULFILLED, CANCELLED -> {
                log.info("Reservation " + entityId + ", in SELECTING, got " + (command.available() ? "yes " : "no ") + "from resource " + command.resourceId);
                String reservationId = currentState().reservationId();
                Reservation reservation = new Reservation(currentState().emails(), currentState().dateTime());
                return effects()
                    .persist(new ReservationEvent.AvailabilityReplied(command.resourceId(), reservationId,
                        reservation, command.available()))
                    .thenReply(newState -> "OK");
            }
            case INIT -> {
                return effects().error("Reservation " + entityId + " in INIT state cannot possibly receive availability replies yet");
            }
            default -> {
                return effects().error("This should never happen for reservation entity " + entityId);
            }

        }
    }

    public Effect<String> fulfill(Fulfill command) {
        log.info("Reservation {} gets confirmation from resource {}", entityId, command.resourceId());
        return switch (currentState().state()) {
            case SELECTING -> effects()
                .persist(new ReservationEvent.Fulfilled(command.resourceId(),
                    entityId, command.reservation(), currentState().selection()))
                .thenReply(newState -> "OK, picked resource " + command.resourceId());
            case INIT, COLLECTING, FULFILLED, CANCELLED, UNAVAILABLE -> effects().reply("Resource cannot be booked");
        };

    }

    public ReadOnlyEffect<ReservationState> getReservation() {
        return effects().reply(currentState());
    }

    public Effect<ReservationId> cancelRequest() {
        log.info("Cancelling reservation {} requested", entityId);
        return switch (currentState().state()) {
            case FULFILLED, COLLECTING -> {
                String resourceId = getResourceIdFromState();
                yield effects()
                    .persist(new ReservationEvent.CancelRequested(entityId,
                        resourceId, currentState().dateTime()))
                    .thenReply(newState -> new ReservationId(entityId));
            }
            default ->
                effects().error("Reservation entity " + entityId + " must be in fulfilled state to be cancelled");
        };
    }

    private String getResourceIdFromState() {
        ReservationState state = currentState();
        return state.resourceId();
    }

    public Effect<String> cancel() {
        log.info("Cancelling of reservation {} is confirmed", entityId);
        switch (currentState().state()) {
            case FULFILLED, COLLECTING -> {
                String resourceId = getResourceIdFromState();
                return effects()
                    .persist(new ReservationEvent.ReservationCancelled(
                        entityId,
                        fromReservationState(currentState()),
                        resourceId, currentState().selection()))
                    .thenReply(newState -> entityId);
            }
            default -> {
                return effects().error("reservation entity " + entityId + " was not in fulfilled state");
            }
        }
    }

    public Effect<String> expire() {
        log.info("Reservation {} is asked to expire for timeout", entityId);
        return switch (currentState().state()) {
            case INIT, COLLECTING, SELECTING -> effects()
                .persist(new ReservationEvent.SearchExhausted(
                    entityId,
                    new Reservation(currentState().emails(), currentState().dateTime()),
                    currentState().selection()))
                .thenReply(newState -> entityId);
            case FULFILLED, CANCELLED, UNAVAILABLE -> effects().reply("OK");
        };
    }

    public Effect<String> reject(Reject command) {

        switch (currentState().state()) {
            case SELECTING -> {
                String resourceId = command.resourceId();
                if(currentState().hasAvailableResources()) {
                    String nextResourceId = currentState().pop();
                    log.info("Reservation {} was rejected (resource {}) and will try another resource", entityId, resourceId);
                    return effects().persist(new ReservationEvent.RejectedWithNext(entityId, resourceId, nextResourceId))
                        .thenReply(newState -> "OK");
                } else {
                    log.info("Reservation {} was rejected (resource {}) but there is nothing left available to reserve", entityId, resourceId);
                    return effects().persist(new ReservationEvent.Rejected(entityId, resourceId))
                        .thenReply(newState -> "OK");
                }
            }
            default -> {
                return effects().error("Reservation " + entityId + " is completed");
            }
        }
    }

    public record Init(Reservation reservation, Set<String> selection) {}
    public record ReservationId(String reservationId) {}

    public record ReplyAvailability(String reservationId, String resourceId, boolean available) {}
    public record Reject(String resourceId) {}

    public record Fulfill(String resourceId, String reservationId, Reservation reservation) {}
}
