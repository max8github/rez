package com.rez.facility.view;


import com.rez.facility.domain.Address;

public record FacilityResource(
        String name,
        Address address,
        String id,
        String facilityId,
        String resourceId,
        String resourceName,
        String[] timeWindow) {}
