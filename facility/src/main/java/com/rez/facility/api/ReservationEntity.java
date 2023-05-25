package com.rez.facility.api;

import com.rez.facility.domain.ReservationState;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
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
        return new ReservationState(INIT, entityId, "",  "", 0, 0, Collections.emptyList());
    }

    @PostMapping("/init")
    public Effect<String> create(@RequestBody FacilityAction.InitiateReservation command) {
        switch (currentState().state()) {
            case INIT:
                    return effects()
                            .emitEvent(new ReservationEvent.ReservationInitiated(command.reservationId(),
                                    command.reservation(), command.facilityId(), command.resources()))
                            .thenReply(newState -> "OK");
            default:
                return effects().error("reservation entity " + command.reservationId() + " already initiated");
        }
    }

    @PostMapping("/kickoff")
    public Effect<String> kickoff(@RequestBody ReservationAction.KickoffBooking command) {
        switch (currentState().state()) {
            case INIT:
            case SELECTING:
                var i = currentState().numOfResponses();
                var resources = currentState().resources();
                if (i < resources.size()) {
                    var resourceId = currentState().resources().get(i);
                    return effects()
                            .emitEvent(new ReservationEvent.ResourceSelected(resourceId, command.reservation(),
                                    command.reservationId(), command.facilityId()))
                            .thenReply(newState -> "OK");
                } else {
                    return effects()
                            .emitEvent(new ReservationEvent.ReservationRejected(command.reservationId(),
                                    command.reservation(), command.facilityId()))
                            .thenReply(newState -> "Not Available");
                }
            default:
                return effects().error("reservation entity " + command.reservationId() + " already kicked off");
        }
    }

    @PostMapping("/book")
    public Effect<String> book(@RequestBody ReservationAction.Book command) {
            return effects()
                    .emitEvent(new ReservationEvent.Booked(command.resourceId(),
                            command.reservation(), command.reservationId()))
                    .thenReply(newState -> "OK");
    }

    @PostMapping("/reject")
    public Effect<String> reject(@RequestBody ReservationAction.Reject command) {
//        var i = currentState().numOfResponses();
//        var who = command.reservationId();
//        var nextResourceId = "somehow next resource id here";
//        if(currentState().resources().size() - i > 1) {
//            return effects()
//                    .emitEvent(new ReservationEvent.ResourceSelected(nextResourceId, command.reservation(),
//                            command.reservationId()))
//                    .thenReply(newState -> "OK");
//        } else {
//            //we are unavailable, finito
//        }
            return effects()
                    .emitEvent(new ReservationEvent.ReservationRejected(command.reservationId(),
                            command.reservation(), command.facilityId()))
                    .thenReply(newState -> "Not Available");
    }

    @GetMapping()
    public Effect<ReservationState> getReservation() {
        return effects().reply(currentState());
    }

    @EventHandler
    public ReservationState initiated(ReservationEvent.ReservationInitiated event) {
        return event.reservation()
                .toReservationState(event.reservationId(), event.facilityId(), event.resources())
                .withState(SELECTING);
    }

    @EventHandler
    public ReservationState resourceSelected(ReservationEvent.ResourceSelected event) {
        return currentState().withState(SELECTING);
    }

    @EventHandler
    public ReservationState booked(ReservationEvent.Booked event) {
        return currentState().withState(DONE);
    }

    @EventHandler
    public ReservationState rejected(ReservationEvent.ReservationRejected event) {
        return currentState().withState(UNAVAILABLE);
    }

    @EventHandler
    public ReservationState reservationRejected(ReservationEvent.ReservationRejected event) {
        return currentState().withState(UNAVAILABLE);
    }
}