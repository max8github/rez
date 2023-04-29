package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

public sealed interface ResourceEvent {

    @TypeName("resource-created")
    record ResourceCreated(String entityId, Dto.ResourceDTO resourceDTO, String facilityId) implements ResourceEvent {}
}