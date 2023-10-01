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
        return effects()
                .emitEvent(new ResourceEvent.ResourceCreated(entityId, resCommand.resourceDto(), resCommand.facilityId()))
                .thenReply(newState -> "OK");
    }

    @EventHandler
    public ResourceState created(ResourceEvent.ResourceCreated resourceCreated) {
        Resource resource = resourceCreated.resourceDto();
        return ResourceState.initialize(resource.resourceName());
    }

    @PostMapping("/inquireBooking")
    public Effect<String> inquireBooking(@RequestBody InquireBooking command) {
        if(currentState().fitsInto(command.reservationDto().dateTime())) {
            log.info("Resource {} {} accepts reservation {} ", currentState().name(), entityId, command.reservationId);
            return effects()
                    .emitEvent(new ResourceEvent.BookingAccepted(command.resourceId(), command.reservationId(),
                            command.facilityId(), command.reservationDto()))
                    .thenReply(newState -> "OK");
        } else {
            log.info("Resource {} {} rejects reservation {}", currentState().name(), entityId, command.reservationId);
            return effects()
                    .emitEvent(new ResourceEvent.BookingRejected(command.resourceId(), command.reservationId(),
                            command.facilityId(), command.reservationDto()
                    ))
                    .thenReply(newState -> "UNAVAILABLE");

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

    @EventHandler
    public ResourceState bookingCanceled(ResourceEvent.BookingCanceled event) {
        return currentState().cancel(event.dateTime(), event.reservationId());
    }

    @EventHandler
    public ResourceState bookingAccepted(ResourceEvent.BookingAccepted event) {
        return currentState().set(event.reservationDto().dateTime(), event.reservationId());
    }

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

    //todo: value obj
    public record InquireBooking(String resourceId, String reservationId, String facilityId, Reservation reservationDto) {}
}