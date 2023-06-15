package com.rez.facility.view;


import com.rez.facility.domain.Address;

public record FacilityResource(
        String facilityId,
        String name,
        Address address,
        String resourceId,
        String resourceName
        ) {}
