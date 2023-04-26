package com.rez.facility.api;

import com.rez.facility.domain.Facility;
import com.rez.facility.domain.FacilityEvent;
import com.rez.facility.domain.Resource;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

// tag::class[]
@EntityKey("facilityId")
@EntityType("shopping-facility")
@RequestMapping("/facility/{facilityId}")
public class FacilityEntity extends EventSourcedEntity<Facility, FacilityEvent> {

    private final String entityId;

    public FacilityEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public Facility emptyState() {
        return new Facility(entityId, Collections.emptyList(), false);
    }

    @PostMapping("/create")
    public Effect<String> create() {
        return effects().reply("OK");
    }

    @PostMapping("/add")
    public Effect<String> addResource(@RequestBody Resource resource) {
        if (currentState().booked())
            return effects().error("Cart is already checked out.");
        if (resource.size() <= 0) {
            return effects().error("Quantity for resource " + resource.resourceId() + " must be greater than zero.");
        }

        var event = new FacilityEvent.ResourceAdded(resource);

        return effects()
                .emitEvent(event)
                .thenReply(newState -> "OK");
    }


    @PostMapping("/resources/{resourceId}/remove")
    public Effect<String> removeResource(@PathVariable String resourceId) {
        if (currentState().booked())
            return effects().error("Facility is already booked.");
        if (currentState().findResourceById(resourceId).isEmpty()) {
            return effects().error("Cannot remove resource " + resourceId + " because it is not in the facility.");
        }

        var event = new FacilityEvent.ResourceRemoved(resourceId);

        return effects()
                .emitEvent(event)
                .thenReply(newState -> "OK");
    }

    @GetMapping()
    public Effect<Facility> getCart() {
        return effects().reply(currentState());
    }

    @PostMapping("/book")
    public Effect<String> book() {
        if (currentState().booked())
            return effects().error("Cart is already checked out.");

        return effects()
                .emitEvent(new FacilityEvent.Booked())
                .deleteEntity()
                .thenReply(newState -> "OK");
    }

    @EventHandler
    public Facility resourceAdded(FacilityEvent.ResourceAdded resourceAdded) {
        return currentState().onResourceAdded(resourceAdded);
    }

    @EventHandler
    public Facility resourceRemoved(FacilityEvent.ResourceRemoved resourceRemoved) {
        return currentState().onResourceRemoved(resourceRemoved);
    }

    @EventHandler
    public Facility booked(FacilityEvent.Booked booked) {
        return currentState().onBooked();
    }
}