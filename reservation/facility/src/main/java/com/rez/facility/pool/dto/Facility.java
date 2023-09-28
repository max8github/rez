package com.rez.facility.pool.dto;

import java.util.Set;

public record Facility(String name, Address address, Set<String> resourceIds) {

    public com.rez.facility.pool.Facility toFacilityState(String entityId) {
        return com.rez.facility.pool.Facility.create(entityId)
                .withName(name)
                .withAddress(address.toAddressState())
                .withResourceIds(resourceIds);
    }

    public static Facility fromFacilityState(com.rez.facility.pool.Facility facilityState) {
        return new Facility(facilityState.name(), Address.fromAddressState(facilityState.address()), facilityState.resourceIds());
    }
}
