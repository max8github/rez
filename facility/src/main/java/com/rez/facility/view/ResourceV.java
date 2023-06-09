package com.rez.facility.view;

import com.rez.facility.events.ResourceEvent;

public record ResourceV(String facilityId, String resourceId, String resourceName, String[] timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.entityId();
        com.rez.facility.domain.Resource resource = created.resource() == null
                ? com.rez.facility.domain.Resource.initialize("noname", 0)
                : created.resource().toResourceState();
        return new ResourceV(facilityId, resourceId, resource.name(), resource.timeWindow());
    }

    public ResourceV withBooking (int timeSlot, String fill){
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = fill;
        return this;
    }

    public ResourceV withoutBooking (int timeSlot){
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = "";
        return this;
    }
}