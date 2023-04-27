package com.rez.facility.api;

import com.rez.facility.domain.*;
import io.grpc.Status;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.springframework.web.bind.annotation.*;

@EntityType("resource")
@EntityKey("resource_id")
@RequestMapping("/resource/{resource_id}")
public class ResourceEntity extends EventSourcedEntity<Resource, ResourceEvent> {
    private final String entityId;

    public ResourceEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }
    @Override
    public Resource emptyState() {
        return new Resource(entityId, new String[24], 24, 0);
    }
    @PostMapping("/create")
    public Effect<String> create(@RequestBody FacilityAction.CreateResourceCommand resCommand) {
        return effects()
                .emitEvent(new ResourceEvent.Created(entityId, resCommand.resourceDTO(), resCommand.facilityId()))
                .thenReply(newState -> "OK");
    }

    @GetMapping()
    public Effect<Resource> getResource() {
        if (currentState() == null)
            return effects().error(
                    "No resource found for id '" + commandContext().entityId() + "'",
                    Status.Code.NOT_FOUND
            );
        else
            return effects().reply(currentState());
    }

    @EventHandler
    public Resource created(ResourceEvent.Created created) {
        return currentState().onCreated(created);
    }
}