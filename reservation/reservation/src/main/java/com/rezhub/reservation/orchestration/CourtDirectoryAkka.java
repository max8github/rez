package com.rezhub.reservation.orchestration;

import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.view.ResourcesByFacilityView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves court resources for a facility by querying ResourcesByFacilityView.
 * In Phase 6, this will be replaced by an HTTP client calling catalog-service.
 */
public class CourtDirectoryAkka implements CourtDirectory {
    private static final Logger log = LoggerFactory.getLogger(CourtDirectoryAkka.class);

    private final ComponentClient componentClient;

    public CourtDirectoryAkka(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public CourtBookingScope resolveScope(BookingContext context) {
        String facilityId = context.scopeId();
        log.debug("Resolving court scope for facilityId={}", facilityId);

        Set<String> resourceIds = componentClient.forView()
            .method(ResourcesByFacilityView::getByFacilityId)
            .invoke(facilityId)
            .entries().stream()
            .map(ResourcesByFacilityView.Row::resourceId)
            .collect(Collectors.toSet());

        return new CourtBookingScope(facilityId, context.timezone(), resourceIds, Map.of());
    }
}
