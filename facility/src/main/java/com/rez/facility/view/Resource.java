package com.rez.facility.view;

public record Resource(String facilityId, String resourceId, String name, String[] timeWindow, int size, int nowPointer) {
    public static Resource initialize(String facilityId, String resourceId, com.rez.facility.domain.Resource resource) {
        return new Resource(facilityId, resourceId, resource.name(), resource.timeWindow(), resource.size(),
                resource.nowPointer());
    }
    public Resource withTimeWindow(int timeSlot, String username) {
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = username;
        return this;
    }
}