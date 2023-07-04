package com.rez.facility.spi;

import com.google.api.services.calendar.Calendar;
import com.rez.facility.api.Mod;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface CalendarSender {
    Logger log = LoggerFactory.getLogger(CalendarSender.class);

    CompletionStage<Mod.ReservationResult> saveToGoogle(Calendar service, Mod.EventDetails eventDetails) throws IOException;

    CompletionStage<Mod.CalendarEventDeletionResult> deleteFromGoogle(Calendar service, String calendarId, String calEventId);

    //todo: This could be part of FacilityEntity's state: makes sense to have a facility calendar there
    static String calendarUrl(List<String> resourceIds) {
        String urlString =  "http://example.com";
        if(resourceIds != null && !resourceIds.isEmpty()) {
            Config googleConfig = ConfigFactory.defaultApplication().getConfig("google");
            String host = googleConfig.getString("host");
            String scheme = googleConfig.getString("scheme");
            String path = googleConfig.getString("path");
            String countryZone = googleConfig.getString("ctz");
            String srcTail = googleConfig.getString("srcTail");
            UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                    .scheme(scheme)
                    .host(host)
                    .path(path)
                    .queryParam("ctz", countryZone);
            resourceIds.stream().forEach(e -> builder.queryParam("src", e + srcTail));
            try {
                urlString = builder.build().toUri().toURL().toString();
            } catch (Exception e) {
                log.error("URL parsing failed", e);
                return urlString;
            }
        }
        return urlString;
    }
}
