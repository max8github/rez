package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.customer.facility.AddressState;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;

/**
 * HTTP endpoint for Facility operations.
 */
@HttpEndpoint("/facility")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class FacilityEndpoint {

    private final ComponentClient componentClient;

    public FacilityEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/{facilityId}")
    public String createFacility(String facilityId, Facility facility) {
        return componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(facility);
    }

    @Get("/{facilityId}")
    public Facility getFacility(String facilityId) {
        return componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::getFacility)
            .invoke();
    }

    @Put("/{facilityId}/name")
    public String renameFacility(String facilityId, RenameRequest request) {
        return componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::rename)
            .invoke(request.name());
    }

    @Put("/{facilityId}/address")
    public String changeAddress(String facilityId, AddressState address) {
        return componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::changeAddress)
            .invoke(address);
    }

    @Post("/{facilityId}/resources/{resourceId}")
    public String registerResource(String facilityId, String resourceId) {
        return componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::registerResource)
            .invoke(resourceId);
    }

    @Delete("/{facilityId}/resources/{resourceId}")
    public String unregisterResource(String facilityId, String resourceId) {
        return componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::unregisterResource)
            .invoke(resourceId);
    }

    public record RenameRequest(String name) {}
}
