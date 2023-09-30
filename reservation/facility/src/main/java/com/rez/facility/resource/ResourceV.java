package com.rez.facility.resource;

import java.time.LocalDateTime;

public record ResourceV(String facilityId, String resourceId, String resourceName, String[] timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.entityId();
        com.rez.facility.resource.dto.Resource resourceDto = created.resourceDto();
        ResourceState resourceState = resourceDto == null
                ? ResourceState.initialize("noname", 0)
                : ResourceState.initialize(resourceDto.resourceName(), resourceDto.size());
        return new ResourceV(facilityId, resourceId, resourceState.name(), resourceState.timeWindow());
    }

    ResourceV withBooking (LocalDateTime dateTime, String fill){
        int timeSlot = ResourceState.toTimeSlot(dateTime);
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = fill;
        return this;
    }

    ResourceV withoutBooking (LocalDateTime dateTime){
        int timeSlot = ResourceState.toTimeSlot(dateTime);
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = "";
        return this;
    }
}