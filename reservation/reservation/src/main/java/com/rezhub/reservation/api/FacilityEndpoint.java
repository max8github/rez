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
import com.rezhub.reservation.view.FacilityByBotTokenView;

import java.util.List;
import java.util.UUID;

/**
 * HTTP endpoint for Facility operations.
 *
 * IDs are generated internally — callers never supply entity IDs.
 *
 * Provisioning a facility with its courts:
 *   POST /facility                                    { "name": "Padel Club", "address": {...}, "timezone": "Europe/Berlin", "botToken": "..." }
 *   → returns generated facilityId, e.g. "abc123"
 *
 *   POST /facility/abc123/resource                   { "name": "Court 1", "calendarId": "xxx@group.calendar.google.com" }
 *   POST /facility/abc123/resource                   { "name": "Court 2", "calendarId": "yyy@group.calendar.google.com" }
 */
@HttpEndpoint("/facility")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class FacilityEndpoint {

    private final ComponentClient componentClient;

    public FacilityEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/")
    public String createFacility(Facility facility) {
        validateUniqueBotToken(facility.botToken());
        String id = UUID.randomUUID().toString().replace("-", "");
        componentClient
            .forEventSourcedEntity(id)
            .method(FacilityEntity::create)
            .invoke(facility);
        return id;
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

    @Delete("/{facilityId}/botToken")
    public String clearBotToken(String facilityId) {
        return componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::clearBotToken)
            .invoke();
    }

    /**
     * Create a resource (court) and register it with the facility in one step.
     * The resource ID is generated internally and returned to the caller.
     */
    @Post("/{facilityId}/resource")
    public String createAndRegisterResource(String facilityId, CreateResourceRequest request) {
        String resourceId = UUID.randomUUID().toString().replace("-", "");
        var command = new FacilityEntity.CreateAndRegisterResource(request.name(), resourceId, request.calendarId());
        componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::requestResourceCreateAndRegister)
            .invoke(command);
        return resourceId;
    }

    /** Register an already-existing resource with a facility. */
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
    public record CreateResourceRequest(String name, String calendarId) {}

    private void validateUniqueBotToken(String botToken) {
        if (botToken == null || botToken.isBlank()) {
            return;
        }

        List<FacilityByBotTokenView.Entry> matches = componentClient.forView()
            .method(FacilityByBotTokenView::getAllByBotToken)
            .invoke(botToken)
            .entries();

        if (!matches.isEmpty()) {
            throw new IllegalArgumentException(
                "Bot token is already assigned to facility " + matches.get(0).facilityId());
        }
    }

}
