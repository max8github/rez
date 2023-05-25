package com.rez.facility.api;

import com.rez.facility.domain.ReservationState;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

import static com.rez.facility.domain.ReservationState.State.*;

@EntityKey("reservationId")
@EntityType("reservation")
@RequestMapping("/reservation/{reservationId}")
public class ReservationEntity extends EventSourcedEntity<ReservationState, ReservationEvent> {

    private final String entityId;

    public ReservationEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public ReservationState emptyState() {
        return new ReservationState(INIT, entityId, "", "", 0, -1, Collections.emptyList());
    }

    @PostMapping("/init")
    public Effect<String> create(@RequestBody InitiateReservation command) {
        switch (currentState().state()) {
            case INIT:
                    return effects()
                            .emitEvent(new ReservationEvent.ReservationInitiated(command.reservationId(),
                                    command.facilityId(), command.reservation(), command.resources()))
                            .thenReply(newState -> "OK");
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

    @PostMapping("/kickoff")
    public Effect<String> kickoff(@RequestBody KickoffBooking command) {
        switch (currentState().state()) {
            case INIT:
            case SELECTING:
                var nextIndex = currentState().currentResourceIndex() + 1;
                if(currentState().resources().size() > nextIndex) {
                    var nextResourceId = currentState().resources().get(nextIndex);
                    return effects()
                            .emitEvent(new ReservationEvent.ResourceSelected(nextIndex, nextResourceId,
                                    command.reservationId(), command.facilityId(), command.reservation))
                            .thenReply(newState -> "OK");
                } else {
                    return effects()
                            .emitEvent(new ReservationEvent.ReservationRejected(command.reservationId(),
                                    command.facilityId(), command.reservation()))
                            .thenReply(newState -> "Not Available");
                }
            case DONE:
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
        return currentState().withState(SELECTING);
    }

    @EventHandler
    public ReservationState reservationRejected(ReservationEvent.ReservationRejected event) {
        return currentState().withState(UNAVAILABLE);
    }

    @PostMapping("/book")
    public Effect<String> book(@RequestBody Book command) {
            return effects()
                    .emitEvent(new ReservationEvent.Booked(command.resourceId(),
                            command.reservationId(), command.reservation()))
                    .thenReply(newState -> "OK, picked resource " + command.resourceId());
    }

    @EventHandler
    public ReservationState booked(ReservationEvent.Booked event) {
        return currentState().withState(DONE);
    }

    @GetMapping()
    public Effect<ReservationState> getReservation() {
        return effects().reply(currentState());
    }

    public record InitiateReservation(String reservationId, String facilityId, Mod.Reservation reservation,
                                      List<String> resources) {}

    public record KickoffBooking(String reservationId, String facilityId, Mod.Reservation reservation) {}

    public record Book(String resourceId, String reservationId, Mod.Reservation reservation) {}

    public record Reject(String resourceId, String reservationId, String facilityId, Mod.Reservation reservation) {}
}