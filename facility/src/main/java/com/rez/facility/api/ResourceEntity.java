package com.rez.facility.api;

import com.rez.facility.domain.*;
import io.grpc.Status;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.springframework.web.bind.annotation.*;

@EntityType("resource")
@EntityKey("resource_id")
@RequestMapping("/resource/{resource_id}")
public class ResourceEntity extends EventSourcedEntity<Resource, ResourceEvent> {
    private final String entityId;

    public ResourceEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public Resource emptyState() {
        return new Resource(entityId, new String[24], 24, 0);
    }

    @PostMapping("/create")
    public Effect<String> create(@RequestBody FacilityAction.CreateResourceCommand resCommand) {
        return effects()
                .emitEvent(new ResourceEvent.ResourceCreated(entityId, resCommand.resource(), resCommand.facilityId()))
                .thenReply(newState -> "OK");
    }

    @EventHandler
    public Resource created(ResourceEvent.ResourceCreated resourceCreated) {
        return resourceCreated.resource().toResourceState();
    }

    @PostMapping("/select")
    public Effect<String> select(@RequestBody ReservationAction.SelectBooking command) {
        if(currentState().hasAvailable(command.reservation())) {
            return effects()
                    .emitEvent(new ResourceEvent.BookingAccepted(command.reservationId(),
                            command.reservation(), command.resourceId()))
                    .thenReply(newState -> "OK");
        } else {
            return effects()
                    .emitEvent(new ResourceEvent.BookingRejected(command.reservationId(), command.reservation(),
                            command.resourceId(), command.facilityId()))
                    .thenReply(newState -> "UNAVAILABLE");

        }
    }

    @EventHandler
    public Resource bookingAccepted(ResourceEvent.BookingAccepted event) {
        return currentState().fill(event.reservation());
    }

    @EventHandler
    public Resource bookingRejected(ResourceEvent.BookingRejected event) {
        return currentState();
    }

    @GetMapping()
    public Effect<Resource> getResource() {
        if (currentState() == null)
            return effects().error(
                    "No resource found for id '" + commandContext().entityId() + "'",
                    Status.Code.NOT_FOUND
            );
        else
            return effects().reply(currentState());
    }
}