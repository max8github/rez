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
 * Provisioning a facility:
 *   POST /facility   { "name": "Padel Club", "address": {...}, "timezone": "Europe/Berlin", "botToken": "..." }
 *   → returns generated facilityId, e.g. "abc123"
 *
 * Provisioning courts (two steps per court via ResourceEndpoint):
 *   POST /resource/{courtId}             { "resourceName": "Court 1", "calendarId": "..." }
 *   PUT  /resource/{courtId}/external-ref { "externalRef": "{courtId}", "externalGroupRef": "{facilityId}" }
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

    public record RenameRequest(String name) {}

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
