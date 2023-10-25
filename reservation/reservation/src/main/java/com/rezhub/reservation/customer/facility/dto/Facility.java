package com.rezhub.reservation.customer.facility.dto;

import java.util.Set;

public record Facility(String name, Address address, Set<String> resourceIds) {}
