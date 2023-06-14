package com.rez.facility.view;

public record ResourceV(String facilityId, String resourceId, String name, String[] timeWindow) {
    public static ResourceV initialize(String facilityId, String resourceId, com.rez.facility.domain.Resource resource) {
        return new ResourceV(facilityId, resourceId, resource.name(), resource.timeWindow());
    }

    public ResourceV withBooking (int timeSlot, String username){
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = username;
        return this;
    }
}