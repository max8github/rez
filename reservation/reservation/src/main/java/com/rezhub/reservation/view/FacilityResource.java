package com.rezhub.reservation.view;

public record FacilityResource(
        String name,
        String facilityId,
        String resourceName,
        String resourceId,
        String[] timeWindow
        ) {}
