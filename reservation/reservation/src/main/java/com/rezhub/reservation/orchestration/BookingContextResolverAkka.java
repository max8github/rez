package com.rezhub.reservation.orchestration;

import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.view.FacilityByBotTokenView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves BookingContext from OriginRequestContext.
 *
 * Handles two origin modes:
 * - "botToken" in attributes (Phase 1.6+, Telegram): looks up facility via FacilityByBotTokenView
 * - "facilityId" in attributes (Phase 1.5 direct, tool-facing): looks up timezone via FacilityEntity
 */
public class BookingContextResolverAkka implements BookingContextResolver {
    private static final Logger log = LoggerFactory.getLogger(BookingContextResolverAkka.class);
    private static final String DEFAULT_TIMEZONE = "Europe/Berlin";
    private static final String DOMAIN_COURTS = "courts";

    private final ComponentClient componentClient;

    public BookingContextResolverAkka(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public BookingContext resolve(OriginRequestContext origin) {
        Map<String, String> attrs = origin.attributes();

        if (attrs.containsKey("botToken")) {
            return resolveFromBotToken(attrs.get("botToken"));
        }
        if (attrs.containsKey("facilityId")) {
            return resolveFromFacilityId(attrs.get("facilityId"));
        }
        throw new IllegalArgumentException("Cannot resolve booking context: no botToken or facilityId in origin attributes");
    }

    private BookingContext resolveFromBotToken(String botToken) {
        Optional<FacilityByBotTokenView.Entry> entry = componentClient.forView()
            .method(FacilityByBotTokenView::getByBotToken)
            .invoke(botToken);
        if (entry.isEmpty()) {
            throw new IllegalStateException("No facility found for botToken: " + botToken);
        }
        String tz = entry.get().timezone() != null ? entry.get().timezone() : DEFAULT_TIMEZONE;
        log.debug("Resolved context from botToken: facilityId={}, timezone={}", entry.get().facilityId(), tz);
        return new BookingContext(DOMAIN_COURTS, entry.get().facilityId(), tz, Map.of());
    }

    private BookingContext resolveFromFacilityId(String facilityId) {
        try {
            Facility facility = componentClient.forEventSourcedEntity(facilityId)
                .method(FacilityEntity::getFacility)
                .invoke();
            String tz = facility != null && facility.timezone() != null ? facility.timezone() : DEFAULT_TIMEZONE;
            log.debug("Resolved context from facilityId={}, timezone={}", facilityId, tz);
            return new BookingContext(DOMAIN_COURTS, facilityId, tz, Map.of());
        } catch (Exception e) {
            log.warn("Could not fetch facility {} for context resolution, using defaults: {}", facilityId, e.getMessage());
            return new BookingContext(DOMAIN_COURTS, facilityId, DEFAULT_TIMEZONE, Map.of());
        }
    }
}
