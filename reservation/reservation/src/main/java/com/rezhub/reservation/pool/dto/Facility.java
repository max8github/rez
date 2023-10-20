package com.rezhub.reservation.pool.dto;

import java.util.Set;

public record Facility(String name, Set<String> resourceIds) {

}
