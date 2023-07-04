package com.rez.facility.dto;

import java.util.Set;

public record Facility(String name, Address address, Set<String> resourceIds) {

    public com.rez.facility.domain.Facility toFacilityState(String entityId) {
        return com.rez.facility.domain.Facility.create(entityId)
                .withName(name)
                .withAddress(address.toAddressState())
                .withResourceIds(resourceIds);
    }

    public static Facility fromFacilityState(com.rez.facility.domain.Facility facilityState) {
        return new Facility(facilityState.name(), Address.fromAddressState(facilityState.address()), facilityState.resourceIds());
    }
}
