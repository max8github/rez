package com.rez.facility.view;


import com.rez.facility.pool.Address;

public record FacilityResource(
        String name,
        Address address,
        String facilityId,
        String resourceName,
        String resourceId,
        String[] timeWindow
        ) {}
