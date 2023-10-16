package com.rezhub.reservation.pool;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.pool.dto.Facility;
import com.rezhub.reservation.resource.dto.Resource;
import kalix.javasdk.annotations.*;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.UUID;

@Id("facilityId")
@TypeId("facility")
@RequestMapping("/facility/{facilityId}")
public class FacilityEntity extends EventSourcedEntity<com.rezhub.reservation.pool.Facility, FacilityEvent> {
    private static final Logger log = LoggerFactory.getLogger(FacilityEntity.class);
    private final String entityId;

    public FacilityEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public com.rezhub.reservation.pool.Facility emptyState() {
        return com.rezhub.reservation.pool.Facility.create(entityId).withName("noname");
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/create")
    public Effect<String> create(@RequestBody com.rezhub.reservation.pool.dto.Facility facility) {
        log.info("created facility {}", facility.name());
        return effects()
                .emitEvent(new FacilityEvent.Created(entityId, facility))
                .thenReply(newState -> entityId);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public com.rezhub.reservation.pool.Facility created(FacilityEvent.Created created) {
        var dto = created.facility();
        return com.rezhub.reservation.pool.Facility.create(created.entityId())
                .withName(dto.name())
                .withResourceIds(dto.resourceIds());
    }

    @PostMapping("/rename/{newName}")
    public Effect<String> rename(@PathVariable String newName) {
        return effects()
                .emitEvent(new FacilityEvent.Renamed(newName))
                .thenReply(newState -> "OK");
    }

    @SuppressWarnings("unused")
    @EventHandler
    public com.rezhub.reservation.pool.Facility renamed(FacilityEvent.Renamed renamed) {
        return currentState().withName(renamed.newName());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/resource/submit")
    public Effect<String> submitResource(@RequestBody Resource resourceDto) {
        String id = resourceDto.resourceId();
        return effects()
                .emitEvent(new FacilityEvent.ResourceSubmitted(currentState().facilityId(), resourceDto, id))
                .thenReply(newState -> id);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public com.rezhub.reservation.pool.Facility resourceIdSubmitted(FacilityEvent.ResourceSubmitted event) {
        return currentState();
    }

    @PostMapping("/resource/{resourceId}")
    public Effect<String> addResourceId(@PathVariable String resourceId) {
        log.info("added resource id {}", resourceId);
        return effects()
                .emitEvent(new FacilityEvent.ResourceIdAdded(resourceId))
                .thenReply(newState -> resourceId);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public com.rezhub.reservation.pool.Facility resourceIdAdded(FacilityEvent.ResourceIdAdded event) {
        return currentState().withResourceId(event.resourceEntityId());
    }

    @DeleteMapping("/resource/{resourceId}")
    public Effect<String> removeResourceId(@PathVariable String resourceId) {
        if (!currentState().resourceIds().contains(resourceId)) {
            return effects().error("Cannot remove resource " + resourceId + " because it is not in the facility.");
        }
        return effects()
                .emitEvent(new FacilityEvent.ResourceIdRemoved(resourceId))
                .thenReply(newState -> "OK");
    }

    @SuppressWarnings("unused")
    @EventHandler
    public com.rezhub.reservation.pool.Facility resourceIdRemoved(FacilityEvent.ResourceIdRemoved event) {
        return currentState().withoutResourceId(event.resourceEntityId());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<com.rezhub.reservation.pool.dto.Facility> getFacility() {
        com.rezhub.reservation.pool.Facility facilityState = currentState();
        return effects().reply(new Facility(facilityState.name(), facilityState.resourceIds()));
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/reservation/create")
    public Effect<String> createReservation(@RequestBody Reservation reservation) {
        var reservationId = UUID.randomUUID().toString().replaceAll("-", "");
        log.info("Facility assigns id {} to reservation, datetime {}", reservationId, reservation.dateTime());
        FacilityEvent.ReservationCreated reservationCreated = new FacilityEvent.ReservationCreated(reservationId, commandContext().entityId(), reservation,
                new ArrayList<>(currentState().resourceIds()));
        log.info("Emitting event: " + reservationCreated);
        return effects()
                .emitEvent(reservationCreated)
                .thenReply(newState -> reservationId);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public com.rezhub.reservation.pool.Facility reservationCreated(FacilityEvent.ReservationCreated event) {
        return currentState();
    }
}