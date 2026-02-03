package com.rezhub.reservation.view;

public record FacilityV(String name, String facilityId) {
    public FacilityV withName(String name) {
        return new FacilityV(name, facilityId);
    }

    public FacilityV withFacilityId(String facilityId) {
        return new FacilityV(name, facilityId);
    }
}
