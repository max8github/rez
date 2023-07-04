package com.rez.facility.dto;

public record Resource(String resourceId, String resourceName, int size) {

    public static Resource fromResourceState(com.rez.facility.domain.Resource resourceState, String resourceId) {
        return new Resource(resourceId, resourceState.name(), resourceState.size());
    }

    public com.rez.facility.domain.Resource toResourceState() {
        return com.rez.facility.domain.Resource.initialize(resourceName, size);
    }
}
