package com.rez.facility.api;

import com.rez.facility.domain.Resource;
import io.grpc.Status;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.valueentity.ValueEntity;
import org.springframework.web.bind.annotation.*;

@EntityType("resource")
@EntityKey("resource_id")
@RequestMapping("/resource/{resource_id}")
public class ResourceEntity extends ValueEntity<Resource> {

    @PostMapping("/create")
    public Effect<String> create(@RequestBody Resource resource) {
        if (currentState() == null)
            return effects()
                    .updateState(resource)
                    .thenReply("OK");
        else
            return effects().error("Resource exists already");
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

    @PostMapping("/probeBooking")
    public Effect<String> probeBooking(@RequestBody String todo) {
        Resource updatedResource = currentState().withSize(24);
        return effects().updateState(updatedResource).thenReply("OK");
    }
}