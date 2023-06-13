package com.rez.facility.view;


import com.rez.facility.domain.Address;

import java.time.LocalDate;

public record FacilityResource(
    String resourceId,
    String resourceName,
    int timeSlot,
    String facilityId,
    String email,
    String name,
    Address address,
    LocalDate date) {}
