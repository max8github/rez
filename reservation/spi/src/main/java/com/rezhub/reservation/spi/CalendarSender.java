package com.rezhub.reservation.spi;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public interface CalendarSender {
    Logger log = LoggerFactory.getLogger(CalendarSender.class);

    CompletionStage<ReservationResult> saveToGoogle(EventDetails eventDetails) throws IOException;

    CompletionStage<CalendarEventDeletionResult> deleteFromGoogle(String calendarId, String calEventId);

    static String calendarUrlFromIds(List<String> calendarIds) {
        String urlString = "http://example.com";
        if (calendarIds == null || calendarIds.isEmpty()) return urlString;
        Config googleConfig = ConfigFactory.parseResources("application.conf").getConfig("google");
        String host = googleConfig.getString("host");
        String scheme = googleConfig.getString("scheme");
        String path = googleConfig.getString("path");
        String countryZone = googleConfig.getString("ctz");
        String srcParams = calendarIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .map(id -> "src=" + id)
            .collect(Collectors.joining("&"));
        if (srcParams.isEmpty()) return urlString;
        return scheme + "://" + host + path + "?ctz=" + countryZone + "&" + srcParams;
    }

    record ReservationResult(EventDetails eventDetails, String result, String url) {}

    record CalendarEventDeletionResult(String calendarId, String calEventId) {}

    record EventDetails(String resourceId, String resourceName, String reservationId, String calendarId, String timezone,
                        java.util.Set<String> resourceIds,
                        List<String> emails,
                        LocalDateTime dateTime,
                        String facilityAddress) {}
}
