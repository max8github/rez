package com.rezhub.reservation.spi;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public interface CalendarSender {
    Logger log = LoggerFactory.getLogger(CalendarSender.class);

    CompletionStage<ReservationResult> saveToGoogle(EventDetails eventDetails) throws IOException;

    CompletionStage<CalendarEventDeletionResult> deleteFromGoogle(String calendarId, String calEventId);

    //todo: This could be part of FacilityEntity's state: makes sense to have a facility calendar there
    static String calendarUrl(Set<String> resourceIds) {
        String urlString = "http://example.com";
        if (resourceIds != null && !resourceIds.isEmpty()) {
            Config googleConfig = ConfigFactory.defaultApplication().getConfig("google");
            String host = googleConfig.getString("host");
            String scheme = googleConfig.getString("scheme");
            String path = googleConfig.getString("path");
            String countryZone = googleConfig.getString("ctz");
            String srcTail = googleConfig.getString("srcTail");

            try {
                String srcParams = resourceIds.stream()
                    .map(e -> "src=" + URLEncoder.encode(e + srcTail, StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
                String query = "ctz=" + URLEncoder.encode(countryZone, StandardCharsets.UTF_8) + "&" + srcParams;
                URI uri = new URI(scheme, host, path, query, null);
                urlString = uri.toURL().toString();
            } catch (Exception e) {
                log.error("URL parsing failed", e);
                return urlString;
            }
        }
        return urlString;
    }

    record ReservationResult(EventDetails eventDetails, String result, String url) {}

    record CalendarEventDeletionResult(String calendarId, String calEventId) {}

    record EventDetails(String resourceId, String reservationId, String facilityId,
                        java.util.Set<String> resourceIds,
                        List<String> emails,
                        LocalDateTime dateTime) {}
}
