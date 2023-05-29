package com.rez.facility.api;

import com.rez.facility.domain.ReservationState;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;

@EntityKey("facilityId")
@EntityType("booking")
@RequestMapping("/booking/{facilityId}")
public class CommandSourcedEntity extends EventSourcedEntity<ReservationState, BookCommands.BookCommand> {

    private final String entityId;

    public CommandSourcedEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public ReservationState emptyState() {
        return new ReservationState(ReservationState.State.INIT, entityId, "", "", 0, -1, Collections.emptyList());
    }

    @PostMapping("/calendar")
    public Effect<String> create(@RequestBody BookCommands.BookCommand command) {
                return effects()
                        .emitEvent(new BookCommands.BookCommand(command.reservationId(),
                                command.facilityId(), command.reservation()))
                        .thenReply(newState -> command.reservationId());
    }

    @EventHandler
    public ReservationState bookCommand(BookCommands.BookCommand c) {
        //should cause an idempotent Google Calendar call somehow
        return c.reservation().toReservationState(c.reservationId(), c.facilityId(), Collections.emptyList());
    }
}
