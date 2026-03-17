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
import com.rezhub.reservation.dto.Reservation;

/**
 * HTTP endpoint for Facility operations.
 *
 * Callers supply plain names (e.g. "padel-club", "court-1") with no prefixes.
 * The endpoint prepends the required internal "f_" prefix before routing to
 * entities, keeping the prefix convention invisible externally.
 *
 * Provisioning a facility with its courts (one-step per court):
 *   POST /facility/padel-club                        { "name": "Padel Club", "address": {...} }
 *   POST /facility/padel-club/resource/court-1       { "name": "Court 1" }
 *   POST /facility/padel-club/resource/court-2       { "name": "Court 2" }
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
            .forEventSourcedEntity(toFacilityId(facilityId))
            .method(FacilityEntity::create)
            .invoke(facility);
    }

    @Get("/{facilityId}")
    public Facility getFacility(String facilityId) {
        return componentClient
            .forEventSourcedEntity(toFacilityId(facilityId))
            .method(FacilityEntity::getFacility)
            .invoke();
    }

    @Put("/{facilityId}/name")
    public String renameFacility(String facilityId, RenameRequest request) {
        return componentClient
            .forEventSourcedEntity(toFacilityId(facilityId))
            .method(FacilityEntity::rename)
            .invoke(request.name());
    }

    @Put("/{facilityId}/address")
    public String changeAddress(String facilityId, AddressState address) {
        return componentClient
            .forEventSourcedEntity(toFacilityId(facilityId))
            .method(FacilityEntity::changeAddress)
            .invoke(address);
    }

    /**
     * Create a resource (court) and register it with the facility in one step.
     * This is the recommended provisioning method — it correctly sets the facilityId
     * on the resource so it appears in availability checks.
     */
    @Post("/{facilityId}/resource/{resourceId}")
    public String createAndRegisterResource(String facilityId, String resourceId, CreateResourceRequest request) {
        var command = new FacilityEntity.CreateAndRegisterResource(request.name(), resourceId);
        return componentClient
            .forEventSourcedEntity(toFacilityId(facilityId))
            .method(FacilityEntity::requestResourceCreateAndRegister)
            .invoke(command);
    }

    /** Register an already-existing resource with a facility. */
    @Post("/{facilityId}/resources/{resourceId}")
    public String registerResource(String facilityId, String resourceId) {
        return componentClient
            .forEventSourcedEntity(toFacilityId(facilityId))
            .method(FacilityEntity::registerResource)
            .invoke(resourceId);
    }

    @Delete("/{facilityId}/resources/{resourceId}")
    public String unregisterResource(String facilityId, String resourceId) {
        return componentClient
            .forEventSourcedEntity(toFacilityId(facilityId))
            .method(FacilityEntity::unregisterResource)
            .invoke(resourceId);
    }

    public record RenameRequest(String name) {}
    public record CreateResourceRequest(String name) {}

    // --- internal helpers ---

    static String toFacilityId(String name) {
        rejectReservedPrefix(name);
        return Reservation.FACILITY + name;
    }

    private static void rejectReservedPrefix(String name) {
        if (name.startsWith(Reservation.FACILITY) || name.startsWith(Reservation.RESOURCE)) {
            throw new IllegalArgumentException(
                "Name '" + name + "' must not start with reserved prefixes '" +
                Reservation.FACILITY + "' or '" + Reservation.RESOURCE + "'");
        }
    }
}
