package com.rez.facility.view;


import com.rez.facility.domain.Address;

public record FacilityResource(
        String name,
        Address address,
        String facilityId,
        String resourceName,
        String resourceId,
        String[] timeWindow
        ) {}
