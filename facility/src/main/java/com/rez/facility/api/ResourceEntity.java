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
        return Resource.initialize(entityId, 24);
    }

    @PostMapping("/create")
    public Effect<String> create(@RequestBody CreateResourceCommand resCommand) {
        return effects()
                .emitEvent(new ResourceEvent.ResourceCreated(entityId, resCommand.resource(), resCommand.facilityId()))
                .thenReply(newState -> "OK");
    }

    @EventHandler
    public Resource created(ResourceEvent.ResourceCreated resourceCreated) {
        return resourceCreated.resource().toResourceState();
    }

    @PostMapping("/select")
    public Effect<String> select(@RequestBody SelectBooking command) {
        if(command.reservation().fitsInto(currentState())) {
            return effects()
                    .emitEvent(new ResourceEvent.BookingAccepted(command.resourceId(), command.reservationId(),
                            command.facilityId(), command.reservation()))
                    .thenReply(newState -> "OK");
        } else {
            return effects()
                    .emitEvent(new ResourceEvent.BookingRejected(command.resourceId(), command.reservationId(),
                            command.facilityId(), command.reservation()
                    ))
                    .thenReply(newState -> "UNAVAILABLE");

        }
    }

    @EventHandler
    public Resource bookingAccepted(ResourceEvent.BookingAccepted event) {
        return event.reservation().setInto(currentState());
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

    public record CreateResourceCommand(String facilityId, Api.Resource resource) {}

    public record SelectBooking(String resourceId, String reservationId, String facilityId, Api.Reservation reservation) {}
}