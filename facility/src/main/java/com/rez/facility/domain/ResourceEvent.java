package com.rez.facility.domain;

import com.rez.facility.api.Dto;
import kalix.javasdk.annotations.TypeName;

public sealed interface ResourceEvent {

    @TypeName("created")
    record Created(String entityId, Dto.ResourceDTO resourceDTO, String facilityId) implements ResourceEvent {}
}