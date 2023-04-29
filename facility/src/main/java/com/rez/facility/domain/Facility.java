package com.rez.facility.domain;

import java.util.HashSet;
import java.util.Set;

public record Facility(String facilityId, String name, Address address, Set<String> resourceIds) {

    public Facility withResourceId(String resourceId) {
        Set<String> ids = new HashSet<>(resourceIds);
        ids.add(resourceId);
        return new Facility(facilityId, name, address, ids);
    }

    public Facility withoutResourceId(String resourceId) {
        Set<String> ids = new HashSet<>(resourceIds);
        ids.remove(resourceId);
        return new Facility(facilityId, name, address, ids);
    }

    public Facility withName(String newName) {
        return new Facility(facilityId, newName, address, resourceIds);
    }

    public Facility withAddress(Address newAddress) {
        return new Facility(facilityId, name, newAddress, resourceIds);
    }
}
