package com.rezhub.reservation.resource;

import com.rezhub.reservation.resource.dto.Resource;

import java.time.LocalDateTime;
import java.util.SortedSet;
import java.util.TreeSet;

public record ResourceV(
    String resourceId,
    String resourceName,
    String facilityId,
    String calendarId,
    SortedSet<Resource.Entry> timeWindow,
    String resourceType
) {
    public static ResourceV initialize(ResourceEvent.FacilityResourceCreated created) {
        return new ResourceV(created.resourceId(), created.name(), created.parentId(),
            created.calendarId(), new TreeSet<>(), "");
    }

    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        return new ResourceV(created.resourceId(), created.resourceName(), "",
            created.calendarId() != null ? created.calendarId() : "", new TreeSet<>(), "");
    }

    ResourceV withBooking(LocalDateTime start, LocalDateTime end, String reservationId) {
        timeWindow.add(new Resource.Entry(start.toString(), end.toString(), reservationId));
        return this;
    }

    ResourceV withoutBooking(String reservationId) {
        this.timeWindow.removeIf(e -> e.reservationId().equals(reservationId));
        return this;
    }

    ResourceV withFacilityId(String facilityId) {
        return new ResourceV(resourceId, resourceName, facilityId, calendarId, timeWindow, resourceType);
    }

    ResourceV withResourceType(String type) {
        return new ResourceV(resourceId, resourceName, facilityId, calendarId, timeWindow, type);
    }
}
