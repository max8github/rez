package com.rez.facility.api;

import com.rez.facility.domain.Reservation;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import static com.rez.facility.domain.Reservation.State.*;

@EntityKey("reservationId")
@EntityType("reservation")
@RequestMapping("/reservation/{reservationId}")
public class ReservationEntity extends EventSourcedEntity<Reservation, ReservationEvent> {

    private final String entityId;

    public ReservationEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public Reservation emptyState() {
        return new Reservation(INIT, entityId, "",  "", 0, 0, Collections.emptyList());
    }

    @PostMapping("/init")
    public Effect<String> create(@RequestBody FacilityAction.InitiateReservation command) {
        switch (currentState().state()) {
            case INIT:
                    return effects()
                            .emitEvent(new ReservationEvent.ReservationInitiated(command.reservationId(),
                                    command.reservationDTO(), command.facilityId(), command.resources()))
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
                            .emitEvent(new ReservationEvent.ResourceSelected(resourceId, command.reservationDTO(),
                                    command.reservationId(), command.facilityId()))
                            .thenReply(newState -> "OK");
                } else {
                    return effects()
                            .emitEvent(new ReservationEvent.ReservationRejected(command.reservationId(),
                                    command.reservationDTO(), command.facilityId()))
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
                            command.reservationDTO(), command.reservationId()))
                    .thenReply(newState -> "OK");
    }

    @PostMapping("/reject")
    public Effect<String> reject(@RequestBody ReservationAction.Reject command) {
//        var i = currentState().numOfResponses();
//        var who = command.reservationId();
//        var nextResourceId = "somehow next resource id here";
//        if(currentState().resources().size() - i > 1) {
//            return effects()
//                    .emitEvent(new ReservationEvent.ResourceSelected(nextResourceId, command.reservationDTO(),
//                            command.reservationId()))
//                    .thenReply(newState -> "OK");
//        } else {
//            //we are unavailable, finito
//        }
            return effects()
                    .emitEvent(new ReservationEvent.ReservationRejected(command.reservationId(),
                            command.reservationDTO(), command.facilityId()))
                    .thenReply(newState -> "Not Available");
    }

    @GetMapping()
    public Effect<Reservation> getReservation() {
        return effects().reply(currentState());
    }

    @EventHandler
    public Reservation initiated(ReservationEvent.ReservationInitiated event) {
        return event.reservationDTO()
                .toReservation(event.reservationId(), event.facilityId(), event.resources())
                .withState(SELECTING);
    }

    @EventHandler
    public Reservation resourceSelected(ReservationEvent.ResourceSelected event) {
        return currentState().withState(SELECTING);
    }

    @EventHandler
    public Reservation booked(ReservationEvent.Booked event) {
        return currentState().withState(DONE);
    }

    @EventHandler
    public Reservation rejected(ReservationEvent.ReservationRejected event) {
        return currentState().withState(UNAVAILABLE);
    }

    @EventHandler
    public Reservation reservationRejected(ReservationEvent.ReservationRejected event) {
        return currentState().withState(UNAVAILABLE);
    }
}