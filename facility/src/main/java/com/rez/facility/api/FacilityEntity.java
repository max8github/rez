package com.rez.facility.api;

import com.rez.facility.domain.Address;
import com.rez.facility.domain.Facility;
import com.rez.facility.dto.Reservation;
import com.rez.facility.dto.Resource;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.UUID;

@EntityKey("facilityId")
@EntityType("facility")
@RequestMapping("/facility/{facilityId}")
public class FacilityEntity extends EventSourcedEntity<Facility, FacilityEvent> {
    private static final Logger log = LoggerFactory.getLogger(FacilityEntity.class);
    private final String entityId;

    public FacilityEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public Facility emptyState() {
        return Facility.create(entityId).withName("noname").withAddress(new Address("nostreet", "nocity"));
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/create")
    public Effect<String> create(@RequestBody com.rez.facility.dto.Facility facility) {
        log.info("created facility {}", facility.name());
        return effects()
                .emitEvent(new FacilityEvent.Created(entityId, facility))
                .thenReply(newState -> entityId);
    }

    @EventHandler
    public Facility created(FacilityEvent.Created created) {
        var dto = created.facility();
        return dto.toFacilityState(created.entityId());
    }

    @PostMapping("/rename/{newName}")
    public Effect<String> rename(@PathVariable String newName) {
        return effects()
                .emitEvent(new FacilityEvent.Renamed(newName))
                .thenReply(newState -> "OK");
    }

    @EventHandler
    public Facility renamed(FacilityEvent.Renamed renamed) {
        return currentState().withName(renamed.newName());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/changeAddress")
    public Effect<String> changeAddress(@RequestBody com.rez.facility.dto.Address address) {
        return effects()
                .emitEvent(new FacilityEvent.AddressChanged(address))
                .thenReply(newState -> "OK");
    }

    @EventHandler
    public Facility addressChanged(FacilityEvent.AddressChanged addressChanged) {
        return currentState().withAddress(addressChanged.address().toAddressState());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/resource/submit")
    public Effect<String> submitResource(@RequestBody Resource resource) {
        String id = resource.resourceId();
        return effects()
                .emitEvent(new FacilityEvent.ResourceSubmitted(currentState().facilityId(), resource, id))
                .thenReply(newState -> id);
    }

    @EventHandler
    public Facility resourceIdSubmitted(FacilityEvent.ResourceSubmitted event) {
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
    public Facility resourceIdAdded(FacilityEvent.ResourceIdAdded event) {
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
    public Facility resourceIdRemoved(FacilityEvent.ResourceIdRemoved event) {
        return currentState().withoutResourceId(event.resourceEntityId());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<com.rez.facility.dto.Facility> getFacility() {
        return effects().reply(com.rez.facility.dto.Facility.fromFacilityState(currentState()));
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/reservation/create")
    public Effect<String> createReservation(@RequestBody Reservation reservation) {
        var reservationId = UUID.randomUUID().toString().replaceAll("-", "");
        int timeSlot = reservation.timeSlot();
        log.info("Facility assigns id {} to reservation, date {}, time {}", reservationId, reservation.date(), timeSlot);
        FacilityEvent.ReservationCreated reservationCreated = new FacilityEvent.ReservationCreated(reservationId, commandContext().entityId(), reservation,
                new ArrayList<>(currentState().resourceIds()));
        log.info("Emitting event: " + reservationCreated);
        return effects()
                .emitEvent(reservationCreated)
                .thenReply(newState -> reservationId);
    }

    @EventHandler
    public Facility reservationCreated(FacilityEvent.ReservationCreated event) {
        return currentState();
    }
}