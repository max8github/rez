package com.rez.facility.domain;

import java.util.HashSet;
import java.util.Set;

public record Facility(String facilityId, String name, Address address, Set<String> resourceIds) {

    public static Facility create(String facilityId) {
        return new Facility(facilityId, "", new Address("", ""), new HashSet<>());
    }

    public Facility withFacilityId(String facilityId) {
        Set<String> ids = (resourceIds == null) ? new HashSet<>() : new HashSet<>(resourceIds);
        return new Facility(facilityId, name, address, ids);
    }

    public Facility withResourceId(String resourceId) {
        Set<String> ids = (resourceIds == null) ? new HashSet<>() : new HashSet<>(resourceIds);
        ids.add(resourceId);
        return new Facility(facilityId, name, address, ids);
    }

    public Facility withoutResourceId(String resourceId) {
        Set<String> ids = new HashSet<>(resourceIds);
        ids.remove(resourceId);
        return new Facility(facilityId, name, address, ids);
    }

    public Facility withResourceIds(Set<String> resourceIds) {
        Set<String> ids = new HashSet<>();
        if(resourceIds != null)
            ids = new HashSet<>(resourceIds);
        if(this.resourceIds != null) {
            ids.addAll(this.resourceIds);
        }
        return new Facility(facilityId, name, address, ids);
    }

    public Facility withName(String newName) {
        Set<String> ids = resourceIds == null ? new HashSet<>() : resourceIds;
        return new Facility(facilityId, newName, address, ids);
    }

    public Facility withAddress(Address newAddress) {
        Set<String> ids = resourceIds == null ? new HashSet<>() : resourceIds;
        return new Facility(facilityId, name, newAddress, ids);
    }
}
