package com.rez.facility.view;

public record Resource(String facilityId, String resourceId, String name, int size) {
    public static Resource initialize(String facilityId, String resourceId, com.rez.facility.domain.Resource resource) {
        return new Resource(facilityId, resourceId, resource.name(), resource.size());
    }
}