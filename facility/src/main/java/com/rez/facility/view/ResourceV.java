package com.rez.facility.view;

public record ResourceV(String facilityId, String resourceId, String resourceName, int size) {
    public static ResourceV initialize(String facilityId, String resourceId, com.rez.facility.domain.Resource resource) {
        return new ResourceV(facilityId, resourceId, resource.name(), resource.size());
    }
}