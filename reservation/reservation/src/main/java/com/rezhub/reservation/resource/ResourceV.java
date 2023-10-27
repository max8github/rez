package com.rezhub.reservation.resource;

import com.rezhub.reservation.resource.dto.Resource;

import java.time.LocalDateTime;
import java.util.SortedSet;
import java.util.TreeSet;

public record ResourceV(String resourceId, String resourceName, String facilityId, SortedSet<Resource.Entry> timeWindow) {
    public static ResourceV initialize(ResourceEvent.FacilityResourceCreated created) {
        String poolId = created.parentId();
        String resourceId = created.resourceId();
        return new ResourceV(resourceId, created.name(), poolId, new TreeSet<>());
    }
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        return new ResourceV(created.resourceId(), created.resourceName(), "", new TreeSet<>());
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