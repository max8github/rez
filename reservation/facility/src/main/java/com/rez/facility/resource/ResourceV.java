package com.rez.facility.resource;

import java.time.LocalDateTime;
import java.util.Map;

public record ResourceV(String facilityId, String resourceId, String resourceName, Map<LocalDateTime, String> timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.entityId();
        com.rez.facility.resource.dto.Resource resourceDto = created.resourceDto();
        ResourceState resourceState = resourceDto == null
                ? ResourceState.initialize("noname")
                : ResourceState.initialize(resourceDto.resourceName());
        return new ResourceV(facilityId, resourceId, resourceState.name(), resourceState.timeWindow());
    }

    ResourceV withBooking(LocalDateTime dateTime, String fill) {
        this.timeWindow.put(dateTime, fill);
        return this;
    }

    ResourceV withoutBooking (LocalDateTime dateTime){
        this.timeWindow.remove(dateTime);
        return this;
    }
}