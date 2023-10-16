package com.rezhub.reservation.pool.dto;

import java.util.Set;

public record Pool(String name, Set<String> resourceIds) {

}
