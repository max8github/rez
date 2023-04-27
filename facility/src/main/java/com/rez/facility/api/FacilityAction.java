package com.rez.facility.api;

import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping("/facility/{facilityId}")
public class FacilityAction extends Action {
    private final KalixClient kalixClient;

    public FacilityAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    @PostMapping("/createAndRegisterResource")
    public Effect<String> createAndRegisterResource(@RequestBody Dto.ResourceDTO resourceDTO,
                                                    @PathVariable String facilityId) {
        var resourceEntityId = UUID.randomUUID().toString();
        var command = new CreateResourceCommand(facilityId, resourceDTO);
        var deferredCall =
                kalixClient.post("/resource/" + resourceEntityId + "/create", command, String.class);
        return effects().forward(deferredCall);
    }

    public record CreateResourceCommand(String facilityId, Dto.ResourceDTO resourceDTO) {}
}
