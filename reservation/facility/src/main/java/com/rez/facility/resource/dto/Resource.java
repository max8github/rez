package com.rez.facility.resource.dto;

public record Resource(String resourceId, String resourceName, int size) {

    public com.rez.facility.resource.Resource toResourceState() {
        return com.rez.facility.resource.Resource.initialize(resourceName, size);
    }
}
