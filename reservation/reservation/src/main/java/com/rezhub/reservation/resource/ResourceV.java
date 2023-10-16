package com.rezhub.reservation.resource;

import com.rezhub.reservation.resource.dto.Resource;

import java.time.LocalDateTime;
import java.util.SortedSet;
import java.util.TreeSet;

public record ResourceV(String resourceId, String resourceName, String poolId, SortedSet<Resource.Entry> timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String poolId = created.poolId();
        String resourceId = created.resourceId();
        return new ResourceV(resourceId, created.resourceName(), poolId, new TreeSet<>());
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