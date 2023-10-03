package com.rez.facility.resource;

import com.rez.facility.dto.Reservation;
import com.rez.facility.resource.dto.Resource;
import kalix.javasdk.annotations.*;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Id("resource_id")
@TypeId("resource")
@RequestMapping("/resource/{resource_id}")
public class ResourceEntity extends EventSourcedEntity<ResourceState, ResourceEvent> {
    private static final Logger log = LoggerFactory.getLogger(ResourceEntity.class);
    private final String entityId;

    public ResourceEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public ResourceState emptyState() {
        return ResourceState.initialize(entityId);
    }

    @PostMapping("/create")
    public Effect<String> create(@RequestBody CreateResourceCommand resCommand) {
        String resourceName = resCommand.resourceDto().resourceName();
        return effects()
                .emitEvent(new ResourceEvent.ResourceCreated(entityId, resourceName, resCommand.facilityId()))
                .thenReply(newState -> "OK - " + resourceName);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState created(ResourceEvent.ResourceCreated resourceCreated) {
        return ResourceState.initialize(resourceCreated.resourceName());
    }

    @PostMapping("/inquireBooking")
    public Effect<String> inquireBooking(@RequestBody InquireBooking command) {
        if(currentState().fitsInto(command.reservationDto().dateTime())) {
            log.info("Resource {} {} accepts reservation {} ", currentState().name(), entityId, command.reservationId);
            return effects()
                    .emitEvent(new ResourceEvent.BookingAccepted(entityId, command.reservationId(),
                            command.facilityId(), command.reservationDto()))
                    .thenReply(newState -> "OK");
        } else {
            log.info("Resource {} {} rejects reservation {}", currentState().name(), entityId, command.reservationId);
            return effects()
                    .emitEvent(new ResourceEvent.BookingRejected(entityId, command.reservationId(),
                            command.facilityId(), command.reservationDto()
                    ))
                    .thenReply(newState -> "UNAVAILABLE resource");

        }
    }

    @DeleteMapping("/reservation/{reservationId}/{isoTime}")
    public Effect<String> cancel(@PathVariable String reservationId, @PathVariable String isoTime) {
        LocalDateTime dateTime = LocalDateTime.parse(isoTime);
        log.info("Cancelling reservation {} from resource {} on dateTime {} ", reservationId, entityId, isoTime);
        return effects()
                .emitEvent(new ResourceEvent.BookingCanceled(entityId, reservationId, dateTime))
                .thenReply(newState -> "OK");
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState bookingCanceled(ResourceEvent.BookingCanceled event) {
        return currentState().cancel(event.dateTime(), event.reservationId());
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState bookingAccepted(ResourceEvent.BookingAccepted event) {
        return currentState().set(event.reservationDto().dateTime(), event.reservationId());
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState bookingRejected(ResourceEvent.BookingRejected event) {
        return currentState();
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<ResourceState> getResource() {
        return effects().reply(currentState());
    }

    public record CreateResourceCommand(String facilityId, Resource resourceDto) {}

    public record InquireBooking(String reservationId, String facilityId, Reservation reservationDto) {}
}