package com.rezhub.reservation.calendar;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.resource.ResourceV;
import com.rezhub.reservation.resource.ResourceView;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class CalendarEndpoint extends AbstractHttpEndpoint {
    private static final String DEFAULT_THEME = "default";
    private static final String ETC_EDINGEN_THEME = "etc-edingen";
    private static final String ETC_EDINGEN_NAME = "Erster Tennisclub Edingen-Neckarhausen";

    private final ComponentClient componentClient;

    public CalendarEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Get("/calendar")
    public HttpResponse calendar() {
        return HttpResponses.staticResource("calendar.html");
    }

    @Get("/api/calendar/events")
    public Object calendarEvents() {
        try {
            String facilityId = requiredQueryParam("facilityId");
            String start = parseDateTime("start");
            String end = parseDateTime("end");

            ResourceView.Resources resources = componentClient.forView()
                .method(ResourceView::getResource)
                .invoke(facilityId);
            Map<String, ResourceV> resourcesById = resources.resources().stream()
                .collect(Collectors.toMap(ResourceV::resourceId, Function.identity()));

            List<CalendarEvent> events = componentClient.forView()
                .method(ReservationCalendarView::getEvents)
                .invoke(new ReservationCalendarView.ReservationRange(start, end))
                .reservations().stream()
                .filter(entry -> resourcesById.containsKey(entry.resourceId()))
                .map(entry -> toApi(entry, resourcesById.get(entry.resourceId())))
                .toList();

            return events;
        } catch (IllegalArgumentException e) {
            return HttpResponses.badRequest(e.getMessage());
        }
    }

    @Get("/api/calendar/resources")
    public Object calendarResources() {
        try {
            String facilityId = requiredQueryParam("facilityId");

            ResourceView.Resources resources = componentClient.forView()
                .method(ResourceView::getResource)
                .invoke(facilityId);

            return resources.resources().stream()
                .sorted(java.util.Comparator.comparing(ResourceV::resourceName))
                .map(resource -> new CalendarResource(resource.resourceId(), resource.resourceName()))
                .toList();
        } catch (IllegalArgumentException e) {
            return HttpResponses.badRequest(e.getMessage());
        }
    }

    @Get("/api/calendar/theme")
    public Object calendarTheme() {
        try {
            String facilityId = requiredQueryParam("facilityId");

            Facility facility = componentClient
                .forEventSourcedEntity(facilityId)
                .method(FacilityEntity::getFacility)
                .invoke();

            return new CalendarTheme(isEtcEdingen(facility) ? ETC_EDINGEN_THEME : DEFAULT_THEME);
        } catch (IllegalArgumentException e) {
            return HttpResponses.badRequest(e.getMessage());
        } catch (Exception e) {
            return new CalendarTheme(DEFAULT_THEME);
        }
    }

    private CalendarEvent toApi(ReservationCalendarView.ReservationEntry entry, ResourceV resource) {
        return new CalendarEvent(
            entry.reservationId(),
            entry.resourceId(),
            resource.resourceName(),
            entry.playerNames(),
            entry.startTime(),
            entry.endTime()
        );
    }

    private String requiredQueryParam(String name) {
        return requestContext().queryParams().getString(name)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("Missing query parameter: " + name));
    }

    private String parseDateTime(String name) {
        String value = requiredQueryParam(name);
        try {
            java.time.LocalDateTime.parse(value);
            return value;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid ISO local date-time for query parameter: " + name);
        }
    }

    private boolean isEtcEdingen(Facility facility) {
        return facility != null
            && facility.name() != null
            && ETC_EDINGEN_NAME.equalsIgnoreCase(facility.name().trim());
    }

    public record CalendarEvent(
        String id,
        String resourceId,
        String resourceName,
        String title,
        String start,
        String end
    ) {}

    public record CalendarResource(
        String resourceId,
        String resourceName
    ) {}

    public record CalendarTheme(
        String theme
    ) {}
}
