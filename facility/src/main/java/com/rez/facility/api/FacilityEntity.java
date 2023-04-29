package com.rez.facility.api;

import com.rez.facility.domain.Address;
import com.rez.facility.domain.Facility;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@EntityKey("facilityId")
@EntityType("facility")
@RequestMapping("/facility/{facilityId}")
public class FacilityEntity extends EventSourcedEntity<Facility, FacilityEvent> {

    private final String entityId;

    public FacilityEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public Facility emptyState() {
        return new Facility(entityId, "noname", new Address("nostreet", "nocity"), Collections.emptySet());
    }

    @PostMapping("/create")
    public Effect<String> create(@RequestBody Dto.FacilityDTO facilityDTO) {
        return effects()
                .emitEvent(new FacilityEvent.Created(entityId, facilityDTO))
                .thenReply(newState -> "OK");
    }

    @PostMapping("/rename/{newName}")
    public Effect<String> rename(@PathVariable String newName) {
        return effects()
                .emitEvent(new FacilityEvent.Renamed(newName))
                .thenReply(newState -> "OK");
    }

    @PostMapping("/changeAddress")
    public Effect<String> changeAddress(@RequestBody Dto.AddressDTO addressDTO) {
        return effects()
                .emitEvent(new FacilityEvent.AddressChanged(addressDTO))
                .thenReply(newState -> "OK");
    }

    @PostMapping("/resource/{resourceId}")
    public Effect<String> addResourceId(@PathVariable String resourceId) {
        return effects()
                .emitEvent(new FacilityEvent.ResourceIdAdded(resourceId))
                .thenReply(newState -> "OK");
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

    @GetMapping()
    public Effect<Facility> getFacility() {
        return effects().reply(currentState());
    }

    @EventHandler
    public Facility created(FacilityEvent.Created created) {
        var dto = created.facilityDTO();
        return dto.toFacility(created.entityId());
    }

    @EventHandler
    public Facility renamed(FacilityEvent.Renamed renamed) {
        return currentState().withName(renamed.newName());
    }

    @EventHandler
    public Facility addressChanged(FacilityEvent.AddressChanged addressChanged) {
        return currentState().withAddress(addressChanged.addressDTO().toAddress());
    }

    @EventHandler
    public Facility resourceIdAdded(FacilityEvent.ResourceIdAdded event) {
        return currentState().withResourceId(event.resourceEntityId());
    }

    @EventHandler
    public Facility resourceIdRemoved(FacilityEvent.ResourceIdRemoved event) {
        return currentState().withoutResourceId(event.resourceEntityId());
    }
}