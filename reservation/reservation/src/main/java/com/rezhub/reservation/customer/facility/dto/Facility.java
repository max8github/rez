package com.rezhub.reservation.customer.facility.dto;

import java.util.Set;
import com.rezhub.reservation.customer.dto.Address;

public record Facility(String name, Address address, Set<String> resourceIds) {}
