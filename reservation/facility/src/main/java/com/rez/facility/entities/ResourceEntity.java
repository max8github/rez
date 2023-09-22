package com.rez.facility.entities;

import com.rez.facility.events.ResourceEvent;
import com.rez.facility.domain.*;
import com.rez.facility.dto.Reservation;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@EntityType("resource")
@EntityKey("resource_id")
@RequestMapping("/resource/{resource_id}")
public class ResourceEntity extends EventSourcedEntity<Resource, ResourceEvent> {
    private static final Logger log = LoggerFactory.getLogger(ResourceEntity.class);
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

    @PostMapping("/inquireBooking")
    public Effect<String> inquireBooking(@RequestBody InquireBooking command) {
        if(command.reservation().fitsInto(currentState())) {
            log.info("Resource {} {} accepts reservation {} ", currentState().name(), entityId, command.reservationId);
            return effects()
                    .emitEvent(new ResourceEvent.BookingAccepted(command.resourceId(), command.reservationId(),
                            command.facilityId(), command.reservation()))
                    .thenReply(newState -> "OK");
        } else {
            log.info("Resource {} {} rejects reservation {}", currentState().name(), entityId, command.reservationId);
            return effects()
                    .emitEvent(new ResourceEvent.BookingRejected(command.resourceId(), command.reservationId(),
                            command.facilityId(), command.reservation()
                    ))
                    .thenReply(newState -> "UNAVAILABLE");

        }
    }

    @DeleteMapping("/reservation/{reservationId}/{timeSlot}")
    public Effect<String> cancel(@PathVariable String reservationId, @PathVariable int timeSlot) {
            log.info("Cancelling reservation {} from resource {} on timeSlot {} ", reservationId, entityId, timeSlot);
            return effects()
                    .emitEvent(new ResourceEvent.BookingCanceled(entityId, reservationId, timeSlot))
                    .thenReply(newState -> "OK");
    }

    @EventHandler
    public Resource bookingCanceled(ResourceEvent.BookingCanceled event) {
        return currentState().cancel(event.timeSlot(), event.reservationId());
    }

    @EventHandler
    public Resource bookingAccepted(ResourceEvent.BookingAccepted event) {
        return event.reservation().setInto(currentState(), event.reservationId());
    }

    @EventHandler
    public Resource bookingRejected(ResourceEvent.BookingRejected event) {
        return currentState();
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<com.rez.facility.dto.Resource> getResource() {
            return effects().reply(com.rez.facility.dto.Resource.fromResourceState(currentState(), entityId));
    }

    public record CreateResourceCommand(String facilityId, com.rez.facility.dto.Resource resource) {}

    //todo: value obj
    public record InquireBooking(String resourceId, String reservationId, String facilityId, Reservation reservation) {}
}