package com.rez.facility.resource;

import java.time.LocalDateTime;

public record ResourceV(String facilityId, String resourceId, String resourceName, String[] timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.entityId();
        Resource resource = created.resourceDto() == null
                ? Resource.initialize("noname", 0)
                : created.resourceDto().toResourceState();
        return new ResourceV(facilityId, resourceId, resource.name(), resource.timeWindow());
    }

    ResourceV withBooking (LocalDateTime dateTime, String fill){
        int timeSlot = Resource.toTimeSlot(dateTime);
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = fill;
        return this;
    }

    ResourceV withoutBooking (LocalDateTime dateTime){
        int timeSlot = Resource.toTimeSlot(dateTime);
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = "";
        return this;
    }
}