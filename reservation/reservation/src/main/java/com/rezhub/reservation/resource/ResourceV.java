package com.rezhub.reservation.resource;

import com.rezhub.reservation.resource.dto.Resource;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public record ResourceV(
    String resourceId,
    String resourceName,
    String facilityId,
    String calendarId,
    SortedSet<Resource.Entry> timeWindow,
    Map<DayOfWeek, Set<LocalTime>> weeklySchedule,
    String resourceType
) {
    public static ResourceV initialize(ResourceEvent.FacilityResourceCreated created) {
        return new ResourceV(created.resourceId(), created.name(), created.parentId(),
            created.calendarId(), new TreeSet<>(), new HashMap<>(), "");
    }

    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        return new ResourceV(created.resourceId(), created.resourceName(), "",
            created.calendarId(), new TreeSet<>(), new HashMap<>(), "");
    }

    ResourceV withBooking(LocalDateTime dateTime, String fill) {
        timeWindow.add(new Resource.Entry(dateTime.toString(), fill));
        return this;
    }

    ResourceV withoutBooking(LocalDateTime dateTime) {
        this.timeWindow.remove(new Resource.Entry(dateTime.toString(), ""));
        return this;
    }

    ResourceV withWeeklySchedule(Map<DayOfWeek, Set<LocalTime>> schedule) {
        return new ResourceV(resourceId, resourceName, facilityId, calendarId, timeWindow, schedule, resourceType);
    }

    ResourceV withResourceType(String type) {
        return new ResourceV(resourceId, resourceName, facilityId, calendarId, timeWindow, weeklySchedule, type);
    }
}
