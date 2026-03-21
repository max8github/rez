package com.rezhub.reservation.spi;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public interface CalendarSender {
    Logger log = LoggerFactory.getLogger(CalendarSender.class);

    CompletionStage<ReservationResult> saveToGoogle(EventDetails eventDetails) throws IOException;

    CompletionStage<CalendarEventDeletionResult> deleteFromGoogle(String calendarId, String calEventId);

    static String calendarUrl() {
        Config calendarMap = ConfigFactory.parseResources("application.conf").getConfig("google.resource-calendars");
        return calendarUrl(new java.util.HashSet<>(calendarMap.root().keySet()));
    }

    static String calendarUrl(Set<String> resourceIds) {
        String urlString = "http://example.com";
        if (resourceIds != null && !resourceIds.isEmpty()) {
            Config googleConfig = ConfigFactory.parseResources("application.conf").getConfig("google");
            String host = googleConfig.getString("host");
            String scheme = googleConfig.getString("scheme");
            String path = googleConfig.getString("path");
            String countryZone = googleConfig.getString("ctz");
            Config calendarMap = googleConfig.getConfig("resource-calendars");

            String srcParams = resourceIds.stream()
                .filter(calendarMap::hasPath)
                .map(id -> "src=" + calendarMap.getString(id))
                .collect(Collectors.joining("&"));
            if (srcParams.isEmpty()) return urlString;
            urlString = scheme + "://" + host + path + "?ctz=" + countryZone + "&" + srcParams;
        }
        return urlString;
    }

    static String calendarIdForResource(String resourceId) {
        Config calendarMap = ConfigFactory.parseResources("application.conf").getConfig("google.resource-calendars");
        return calendarMap.hasPath(resourceId) ? calendarMap.getString(resourceId) : resourceId;
    }

    record ReservationResult(EventDetails eventDetails, String result, String url) {}

    record CalendarEventDeletionResult(String calendarId, String calEventId) {}

    record EventDetails(String resourceId, String reservationId, String facilityId,
                        java.util.Set<String> resourceIds,
                        List<String> emails,
                        LocalDateTime dateTime) {}
}
