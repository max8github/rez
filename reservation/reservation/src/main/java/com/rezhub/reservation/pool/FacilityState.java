package com.rezhub.reservation.pool;

import lombok.With;

import java.util.HashSet;
import java.util.Set;

@With
public record FacilityState(String facilityId, String name, Set<String> resourceIds) {

    public static FacilityState create(String facilityId) {
        return new FacilityState(facilityId, "", new HashSet<>());
    }

    public FacilityState withResourceId(String resourceId) {
        Set<String> ids = (resourceIds == null) ? new HashSet<>() : new HashSet<>(resourceIds);
        ids.add(resourceId);
        return new FacilityState(facilityId, name, ids);
    }

    public FacilityState withoutResourceId(String resourceId) {
        Set<String> ids = new HashSet<>(resourceIds);
        ids.remove(resourceId);
        return new FacilityState(facilityId, name, ids);
    }
}
