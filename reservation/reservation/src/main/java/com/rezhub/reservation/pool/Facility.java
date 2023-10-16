package com.rezhub.reservation.pool;

import lombok.With;

import java.util.HashSet;
import java.util.Set;

@With
public record Facility(String facilityId, String name, Set<String> resourceIds) {

    public static Facility create(String facilityId) {
        return new Facility(facilityId, "", new HashSet<>());
    }

    public Facility withResourceId(String resourceId) {
        Set<String> ids = (resourceIds == null) ? new HashSet<>() : new HashSet<>(resourceIds);
        ids.add(resourceId);
        return new Facility(facilityId, name, ids);
    }

    public Facility withoutResourceId(String resourceId) {
        Set<String> ids = new HashSet<>(resourceIds);
        ids.remove(resourceId);
        return new Facility(facilityId, name, ids);
    }
}
