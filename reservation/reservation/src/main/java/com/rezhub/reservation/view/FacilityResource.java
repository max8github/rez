package com.rezhub.reservation.view;


import com.rezhub.reservation.pool.Address;

public record FacilityResource(
        String name,
        Address address,
        String facilityId,
        String resourceName,
        String resourceId,
        String[] timeWindow
        ) {}
