package com.rezhub.reservation.resource;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.dto.Resource;
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
        String id = commandContext().entityId();
        String resourceName = resCommand.resourceDto().resourceName();
        return effects()
                .emitEvent(new ResourceEvent.ResourceCreated(id, resourceName, resCommand.facilityId()))
                .thenReply(newState -> "OK - " + resourceName);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState created(ResourceEvent.ResourceCreated resourceCreated) {
        return ResourceState.initialize(resourceCreated.resourceName());
    }

    @PostMapping("/checkAvailability")
    public Effect<String> checkAvailability(@RequestBody CheckAvailability command) {
        LocalDateTime validTime = ResourceState.roundToValidTime(command.reservation().dateTime());
        boolean vacant = currentState().isReservableAt(validTime);
        String yes = vacant?"":"NOT ";
        log.info("Resource {} ({}) can {}accept reservation {} ", currentState().name(), entityId, yes, command.reservationId);
        return effects()
                .emitEvent(new ResourceEvent.AvalabilityChecked(entityId, command.reservationId(), vacant, command.facilityd()))
                .thenReply(newState -> "OK");
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState availabilityChecked(ResourceEvent.AvalabilityChecked event) {
        return currentState();
    }

    @PostMapping("/reserve")
    public Effect<String> reserve(@RequestBody Reserve command) {
        LocalDateTime validTime = ResourceState.roundToValidTime(command.reservation().dateTime());
        if(currentState().isReservableAt(validTime)) {
            log.info("Resource {} {} accepts reservation {} ", currentState().name(), entityId, command.reservationId);
            return effects()
                    .emitEvent(new ResourceEvent.ReservationAccepted(entityId, command.reservationId(),
                            command.facilityId(), command.reservation()))
                    .thenReply(newState -> "OK");
        } else {
            log.info("Resource {} {} rejects reservation {}", currentState().name(), entityId, command.reservationId);
            return effects()
                    .emitEvent(new ResourceEvent.ReservationRejected(entityId, command.reservationId(),
                            command.facilityId(), command.reservation()
                    ))
                    .thenReply(newState -> "UNAVAILABLE resource");

        }
    }

    @DeleteMapping("/reservation/{reservationId}/{isoTime}")
    public Effect<String> cancel(@PathVariable String reservationId, @PathVariable String isoTime) {
        LocalDateTime dateTime = LocalDateTime.parse(isoTime);
        LocalDateTime validTime = ResourceState.roundToValidTime(dateTime);
        log.info("Cancelling reservation {} from resource {} on dateTime {} ", reservationId, entityId, validTime);
        return effects()
                .emitEvent(new ResourceEvent.ReservationCanceled(entityId, reservationId, validTime))
                .thenReply(newState -> "OK");
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState reservationCanceled(ResourceEvent.ReservationCanceled event) {
        return currentState().cancel(event.dateTime(), event.reservationId());
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState bookingAccepted(ResourceEvent.ReservationAccepted event) {
        return currentState().set(event.reservationDto().dateTime(), event.reservationId());
    }

    @SuppressWarnings("unused")
    @EventHandler
    public ResourceState reservationRejected(ResourceEvent.ReservationRejected event) {
        return currentState();
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<ResourceState> getResource() {
        return effects().reply(currentState());
    }

    public record CreateResourceCommand(String facilityId, Resource resourceDto) {}

    public record CheckAvailability(String reservationId, String facilityd, Reservation reservation) {}

    public record Reserve(String reservationId, Reservation reservation, String facilityId) { }
}