package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.Reservation;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
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
        return new Reservation(reservationState.emails(), reservationState.dateTime(), reservationState.durationMinutes());
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
                    .withResourceIds(e.resourceIds())
                    .withPendingResourceIds(e.resourceIds())
                    .withDateTime(reservation.dateTime())
                    .withDuration(reservation.durationMinutes())
                    .withEmails(reservation.emails())
                    .withRecipientId(e.recipientId())
                    .withOriginSystem(e.originSystem())
                    .withIdentityUserId(e.identityUserId())
                    .withSenderExternalId(e.senderExternalId());
            }
            case ReservationEvent.AvailabilityReplied e -> {
                ReservationState next = currentState().withPendingRemoved(e.resourceId());
                yield e.available()
                    ? next.withAdded(e.resourceId())
                    : next.withRemoved(e.resourceId());
            }
            case ReservationEvent.ResourceSelected e -> currentState()
                .withPendingRemoved(e.resourceId())
                .withRemoved(currentState().resourceId())
                .withAdded(e.resourceId())
                .withResourceId(e.resourceId())
                .withState(SELECTING);
            case ReservationEvent.SearchExhausted e -> {
                log.info("Search exhausted for reservation {}: UNAVAILABLE ", e.reservationId());
                yield currentState()
                    .withPendingRemoved(currentState().resourceId())
                    .withRemoved(currentState().resourceId())
                    .withResourceId("")
                    .withState(UNAVAILABLE);
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
            case ReservationEvent.Rejected e -> {
                log.info("Reservation was rejected, waiting for availability");
                yield currentState().withRemoved(e.resourceId()).withResourceId("").withState(COLLECTING);
            }
            case ReservationEvent.PaymentIdRecorded e -> currentState().withPaymentId(Optional.of(e.paymentId()));
        };
    }

    public Effect<ReservationId> init(Init command) {
        String id = commandContext().entityId();
        ReservationState state = currentState();
        log.info("ReservationEntity initializes with reservation id {}", id);
        return switch (state.state()) {
            case CANCELLED -> effects().error("Reservation cancelled: cannot be initialized");
            case UNAVAILABLE -> effects().error("Reservation was rejected for unavailable selection: cannot be initialized");
            case FULFILLED -> isReplayOfSameRequest(state, command)
                ? effects().reply(new ReservationId(id))
                : effects().error("Reservation had already been accepted: it cannot be reinitialized");
            case COLLECTING, SELECTING -> isReplayOfSameRequest(state, command)
                ? effects().reply(new ReservationId(id))
                : effects().error("Reservation is processing selection: cannot be initialized");
            case INIT -> effects()
                .persist(new ReservationEvent.Inited(id, command.reservation(), command.resourceIds(), command.recipientId(),
                    command.originSystem(), command.identityUserId(), command.senderExternalId()))
                .thenReply(newState -> new ReservationId(id));
        };
    }

    /**
     * A caller retrying {@code init} with the same reservationId after a crash (before it saw the original
     * reply) must not be rejected as "already initialized" — that would send the retrying saga into
     * unnecessary compensation for a reservation that's actually fine. Distinguishes that safe replay from a
     * genuine reservationId collision with a materially different booking request, which must still error
     * rather than silently succeed against the wrong details. CANCELLED/UNAVAILABLE stay hard errors always —
     * a real state change happened since the first attempt, not something to paper over.
     */
    /**
     * Deliberately does not compare identityUserId/senderExternalId: a retry whose resolved identity
     * differs from the first attempt (e.g. the identity service was down on attempt 1, reachable on
     * attempt 2) must still count as a safe replay of the same booking, not a rejected collision.
     * Those two fields describe the requester, not what makes two booking attempts "the same booking."
     */
    private boolean isReplayOfSameRequest(ReservationState state, Init command) {
        Reservation r = command.reservation();
        return state.dateTime().equals(r.dateTime())
            && state.durationMinutes() == r.durationMinutes()
            && state.resourceIds().equals(command.resourceIds())
            && Objects.equals(state.recipientId(), command.recipientId())
            && Objects.equals(state.originSystem(), command.originSystem())
            && Objects.equals(state.emails(), r.emails());
    }

    public Effect<String> replyAvailability(ReplyAvailability command) {
        switch (currentState().state()) {
            case COLLECTING -> {
                log.info("Reservation " + entityId + ", in COLLECTING, got a " + (command.available() ? "YES " : "NO ") + "from resource " + command.resourceId);
                String reservationId = currentState().reservationId();
                Reservation reservation = new Reservation(currentState().emails(), currentState().dateTime(), currentState().durationMinutes());
                if (command.available()) {
                    return effects()
                        .persist(new ReservationEvent.ResourceSelected(command.resourceId(), reservationId, reservation))
                        .thenReply(newState -> "OK");
                } else {
                    if (isLastPendingWithoutCandidates(command.resourceId())) {
                        return effects()
                            .persist(new ReservationEvent.SearchExhausted(
                                entityId, reservation, currentState().resourceIds(), currentState().recipientId(), currentState().originSystem()))
                            .thenReply(newState -> "OK");
                    }
                    return effects()
                        .persist(new ReservationEvent.AvailabilityReplied(command.resourceId(), reservationId,
                            reservation, false))
                        .thenReply(newState -> "OK");
                }
            }
            case SELECTING, UNAVAILABLE, FULFILLED, CANCELLED -> {
                log.info("Reservation " + entityId + ", in SELECTING, got " + (command.available() ? "yes " : "no ") + "from resource " + command.resourceId);
                String reservationId = currentState().reservationId();
                Reservation reservation = new Reservation(currentState().emails(), currentState().dateTime(), currentState().durationMinutes());
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
                    entityId, command.reservation(), currentState().resourceIds(), currentState().recipientId(), currentState().originSystem()))
                .thenReply(newState -> "OK, picked resource " + command.resourceId());
            case INIT, COLLECTING, FULFILLED, CANCELLED, UNAVAILABLE -> effects().reply("Resource cannot be booked");
        };

    }

    public Effect<String> recordPaymentId(String paymentId) {
        return switch (currentState().state()) {
            case FULFILLED -> effects()
                .persist(new ReservationEvent.PaymentIdRecorded(entityId, paymentId))
                .thenReply(newState -> "OK");
            default -> effects().error("Reservation " + entityId + " must be FULFILLED to record a paymentId");
        };
    }

    public ReadOnlyEffect<ReservationState> getReservation() {
        return effects().reply(currentState());
    }

    public Effect<ReservationId> cancelRequest() {
        log.info("Cancelling reservation {} requested", entityId);
        return switch (currentState().state()) {
            case FULFILLED, COLLECTING -> {
                if (currentState().hasEnded()) {
                    yield effects().error("Reservation " + entityId + " has already ended and can no longer be cancelled");
                }
                String resourceId = getResourceIdFromState();
                yield effects()
                    .persist(new ReservationEvent.CancelRequested(entityId, resourceId))
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
                        resourceId, currentState().resourceIds(), currentState().recipientId()))
                    .thenReply(newState -> entityId);
            }
            default -> {
                return effects().error("reservation entity " + entityId + " was not in fulfilled state");
            }
        }
    }

    public Effect<String> reject(Reject command) {

        switch (currentState().state()) {
            case SELECTING -> {
                String resourceId = command.resourceId();
                Optional<String> nextOpt = currentState().availableResources().stream()
                    .filter(id -> !id.equals(resourceId))
                    .findFirst();
                if (nextOpt.isPresent()) {
                    String nextResourceId = nextOpt.get();
                    log.info("Reservation {} was rejected (resource {}) and will try another resource: {}", entityId, resourceId, nextResourceId);
                    Reservation reservation = new Reservation(currentState().emails(), currentState().dateTime(), currentState().durationMinutes());
                    return effects().persist(new ReservationEvent.ResourceSelected(nextResourceId, entityId, reservation))
                        .thenReply(newState -> "OK");
                } else if (currentState().hasPendingResources()) {
                    log.info("Reservation {} was rejected (resource {}) and will keep waiting for pending availability replies", entityId, resourceId);
                    return effects().persist(new ReservationEvent.Rejected(entityId, resourceId))
                        .thenReply(newState -> "OK");
                } else {
                    log.info("Reservation {} was rejected (resource {}) but there is nothing left available to reserve", entityId, resourceId);
                    return effects().persist(new ReservationEvent.SearchExhausted(
                            entityId,
                            fromReservationState(currentState()),
                            currentState().resourceIds(),
                            currentState().recipientId(),
                            currentState().originSystem()))
                        .thenReply(newState -> "OK");
                }
            }
            default -> {
                // Late rejection arriving after the reservation is already completed (fulfilled/cancelled).
                // This is a normal race condition - silently ignore rather than failing the consumer.
                log.info("Reservation {} received late rejection from {} in state {} - ignoring", entityId, command.resourceId(), currentState().state());
                return effects().reply("OK");
            }
        }
    }

    public record Init(Reservation reservation, Set<String> resourceIds, String recipientId, String originSystem,
                       Optional<String> identityUserId, Optional<String> senderExternalId) {}
    public record ReservationId(String reservationId) {}

    public record ReplyAvailability(String reservationId, String resourceId, boolean available) {}
    public record Reject(String resourceId) {}

    public record Fulfill(String resourceId, String reservationId, Reservation reservation) {}

    private boolean isLastPendingWithoutCandidates(String resourceId) {
        return currentState().pendingResourceIds().size() == 1
            && currentState().pendingResourceIds().contains(resourceId)
            && !currentState().hasAvailableResources()
            && currentState().resourceId().isEmpty();
    }
}
