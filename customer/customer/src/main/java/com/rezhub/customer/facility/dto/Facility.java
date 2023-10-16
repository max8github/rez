package com.rezhub.customer.facility.dto;

import java.util.Set;

public record Facility(String name, Address address, Set<String> resourceIds) {}
