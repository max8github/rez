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
            created.calendarId(), new TreeSet<>(), "");
    }

    ResourceV withBooking(LocalDateTime dateTime, String fill) {
        timeWindow.add(new Resource.Entry(dateTime.toString(), fill));
        return this;
    }

    ResourceV withoutBooking(LocalDateTime dateTime) {
        this.timeWindow.remove(new Resource.Entry(dateTime.toString(), ""));
        return this;
    }

    ResourceV withFacilityId(String facilityId) {
        return new ResourceV(resourceId, resourceName, facilityId, calendarId, timeWindow, resourceType);
    }

    ResourceV withResourceType(String type) {
        return new ResourceV(resourceId, resourceName, facilityId, calendarId, timeWindow, type);
    }
}
