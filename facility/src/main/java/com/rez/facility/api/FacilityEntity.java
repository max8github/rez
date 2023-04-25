package com.rez.facility.api;

import com.rez.facility.domain.Address;
import com.rez.facility.domain.Facility;
import com.rez.facility.domain.Resource;
import io.grpc.Status;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.valueentity.ValueEntity;
import org.springframework.web.bind.annotation.*;

@EntityType("facility")
@EntityKey("facility_id")
@RequestMapping("/facility/{facility_id}")
public class FacilityEntity extends ValueEntity<Facility> {

    @PostMapping("/create")
    public Effect<String> create(@RequestBody Facility facility) {
        if (currentState() == null)
            return effects()
                    .updateState(facility)
                    .thenReply("OK");
        else
            return effects().error("Facility exists already");
    }

    @GetMapping()
    public Effect<Facility> getFacility() {
        if (currentState() == null)
            return effects().error(
                    "No facility found for id '" + commandContext().entityId() + "'",
                    Status.Code.NOT_FOUND
            );
        else
            return effects().reply(currentState());
    }

    @PostMapping("/changeName/{newName}")
    public Effect<String> changeName(@PathVariable String newName) {
        Facility updatedFacility = currentState().withName(newName);
        return effects()
                .updateState(updatedFacility)
                .thenReply("OK");
    }

    @PostMapping("/changeAddress")
    public Effect<String> changeAddress(@RequestBody Address newAddress) {
        Facility updatedFacility = currentState().withAddress(newAddress);
        return effects().updateState(updatedFacility).thenReply("OK");
    }

    @PostMapping("/changeResource")
    public Effect<String> changeResource(@RequestBody Resource newResource) {
        Facility updatedFacility = currentState().withResource(newResource);
        return effects().updateState(updatedFacility).thenReply("OK");
    }

}