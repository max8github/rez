package com.rez.facility.api;

import com.rez.facility.domain.Address;
import com.rez.facility.domain.Facility;
import com.rez.facility.domain.FacilityEvent;
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
        return new Facility(entityId, "noname", new Address("nostreet", "nocity"), Collections.emptyList());
    }

    @PostMapping("/create")
    public Effect<String> create(@RequestBody Dto.FacilityDTO facilityDTO) {
        return effects()
                .emitEvent(new FacilityEvent.Created(entityId, facilityDTO))
                .thenReply(newState -> "OK");
    }

    @PostMapping("/add")
    public Effect<String> addResource(@RequestBody Dto.ResourceDTO resourceDetails) {
        if (resourceDetails.size() <= 0) {
            return effects().error("Time slots for resource " + resourceDetails.resourceId() + " must be more than zero.");
        }
        return effects()
                .emitEvent(new FacilityEvent.ResourceAdded(resourceDetails))
                .thenReply(newState -> "OK");
    }


    @PostMapping("/resources/{resourceId}/remove")
    public Effect<String> removeResource(@PathVariable String resourceId) {
        if (currentState().findResourceById(resourceId).isEmpty()) {
            return effects().error("Cannot remove resource " + resourceId + " because it is not in the facility.");
        }
        return effects()
                .emitEvent(new FacilityEvent.ResourceRemoved(resourceId))
                .thenReply(newState -> "OK");
    }

    @GetMapping()
    public Effect<Facility> getFacility() {
        return effects().reply(currentState());
    }

    @EventHandler
    public Facility created(FacilityEvent.Created created) {
        return currentState().onCreated(created);
    }

    @EventHandler
    public Facility resourceAdded(FacilityEvent.ResourceAdded resourceAdded) {
        return currentState().onResourceAdded(resourceAdded);
    }

    @EventHandler
    public Facility resourceRemoved(FacilityEvent.ResourceRemoved resourceRemoved) {
        return currentState().onResourceRemoved(resourceRemoved);
    }
}