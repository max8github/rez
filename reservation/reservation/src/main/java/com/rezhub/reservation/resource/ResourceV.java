package com.rezhub.reservation.resource;

import com.rezhub.reservation.resource.dto.Resource;

import java.time.LocalDateTime;
import java.util.SortedSet;
import java.util.TreeSet;

public record ResourceV(String facilityId, String resourceId, String resourceName, SortedSet<Resource.Entry> timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.resourceId();
        return new ResourceV(facilityId, resourceId, created.resourceName(), new TreeSet<>());
    }

    ResourceV withBooking(LocalDateTime dateTime, String fill) {
        timeWindow.add(new Resource.Entry(dateTime.toString(), fill));
        return this;
    }

    ResourceV withoutBooking (LocalDateTime dateTime){
        this.timeWindow.remove(new Resource.Entry(dateTime.toString(), ""));
        return this;
    }
}