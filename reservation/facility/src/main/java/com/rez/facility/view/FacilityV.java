package com.rez.facility.view;


import com.rez.facility.domain.Address;

public record FacilityV(String name, Address address, String facilityId) {

    public FacilityV withName(String newName) {
        return new FacilityV(newName, address, facilityId);
    }

    public FacilityV withAddress(Address newAddress) {
        return new FacilityV(name, newAddress, facilityId);
    }
}
