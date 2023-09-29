package com.rez.facility.pool;

import com.rez.facility.dto.Reservation;
import com.rez.facility.pool.dto.Address;
import com.rez.facility.pool.dto.Facility;
import com.rez.facility.resource.dto.Resource;
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
public class FacilityEntity extends EventSourcedEntity<com.rez.facility.pool.Facility, FacilityEvent> {
    private static final Logger log = LoggerFactory.getLogger(FacilityEntity.class);
    private final String entityId;

    public FacilityEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public com.rez.facility.pool.Facility emptyState() {
        return com.rez.facility.pool.Facility.create(entityId).withName("noname").withAddress(new com.rez.facility.pool.Address("nostreet", "nocity"));
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/create")
    public Effect<String> create(@RequestBody Facility facility) {
        log.info("created facility {}", facility.name());
        return effects()
                .emitEvent(new FacilityEvent.Created(entityId, facility))
                .thenReply(newState -> entityId);
    }

    @EventHandler
    public com.rez.facility.pool.Facility created(FacilityEvent.Created created) {
        var dto = created.facility();
        return com.rez.facility.pool.Facility.create(created.entityId())
                .withName(dto.name())
                .withAddress(new com.rez.facility.pool.Address(dto.address().street(), dto.address().city()))
                .withResourceIds(dto.resourceIds());
    }

    @PostMapping("/rename/{newName}")
    public Effect<String> rename(@PathVariable String newName) {
        return effects()
                .emitEvent(new FacilityEvent.Renamed(newName))
                .thenReply(newState -> "OK");
    }

    @EventHandler
    public com.rez.facility.pool.Facility renamed(FacilityEvent.Renamed renamed) {
        return currentState().withName(renamed.newName());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/changeAddress")
    public Effect<String> changeAddress(@RequestBody Address address) {
        return effects()
                .emitEvent(new FacilityEvent.AddressChanged(address))
                .thenReply(newState -> "OK");
    }

    @EventHandler
    public com.rez.facility.pool.Facility addressChanged(FacilityEvent.AddressChanged addressChanged) {
        Address address = addressChanged.address();
        return currentState().withAddress(new com.rez.facility.pool.Address(address.street(), address.city()));
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/resource/submit")
    public Effect<String> submitResource(@RequestBody Resource resourceDto) {
        String id = resourceDto.resourceId();
        return effects()
                .emitEvent(new FacilityEvent.ResourceSubmitted(currentState().facilityId(), resourceDto, id))
                .thenReply(newState -> id);
    }

    @EventHandler
    public com.rez.facility.pool.Facility resourceIdSubmitted(FacilityEvent.ResourceSubmitted event) {
        return currentState();
    }

    @PostMapping("/resource/{resourceId}")
    public Effect<String> addResourceId(@PathVariable String resourceId) {
        log.info("added resource id {}", resourceId);
        return effects()
                .emitEvent(new FacilityEvent.ResourceIdAdded(resourceId))
                .thenReply(newState -> resourceId);
    }

    @EventHandler
    public com.rez.facility.pool.Facility resourceIdAdded(FacilityEvent.ResourceIdAdded event) {
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

    @EventHandler
    public com.rez.facility.pool.Facility resourceIdRemoved(FacilityEvent.ResourceIdRemoved event) {
        return currentState().withoutResourceId(event.resourceEntityId());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<Facility> getFacility() {
        com.rez.facility.pool.Facility facilityState = currentState();
        com.rez.facility.pool.Address addressState = facilityState.address();
        Address address = new Address(addressState.street(), addressState.city());
        return effects().reply(new Facility(facilityState.name(), address, facilityState.resourceIds()));
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/reservation/create")
    public Effect<String> createReservation(@RequestBody Reservation reservationDto) {
        var reservationId = UUID.randomUUID().toString().replaceAll("-", "");
        log.info("Facility assigns id {} to reservation, datetime {}", reservationId, reservationDto.dateTime());
        FacilityEvent.ReservationCreated reservationCreated = new FacilityEvent.ReservationCreated(reservationId, commandContext().entityId(), reservationDto,
                new ArrayList<>(currentState().resourceIds()));
        log.info("Emitting event: " + reservationCreated);
        return effects()
                .emitEvent(reservationCreated)
                .thenReply(newState -> reservationId);
    }

    @EventHandler
    public com.rez.facility.pool.Facility reservationCreated(FacilityEvent.ReservationCreated event) {
        return currentState();
    }
}