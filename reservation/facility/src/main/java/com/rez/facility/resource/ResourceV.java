package com.rez.facility.resource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public record ResourceV(String facilityId, String resourceId, String resourceName, Map<LocalDateTime, String> timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.resourceId();
        return new ResourceV(facilityId, resourceId, created.resourceName(), new HashMap<>());
    }

    ResourceV withBooking(LocalDateTime dateTime, String fill) {
        timeWindow.put(dateTime, fill);
        return this;
    }

    ResourceV withoutBooking (LocalDateTime dateTime){
        this.timeWindow.remove(dateTime);
        return this;
    }
}