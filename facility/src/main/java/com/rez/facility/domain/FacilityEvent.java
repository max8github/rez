package com.rez.facility.domain;

import kalix.javasdk.annotations.TypeName;

public sealed interface FacilityEvent {

    @TypeName("resource-added")
    record ResourceAdded(Resource resource) implements FacilityEvent {}

    @TypeName("resource-removed")
    record ResourceRemoved(String resourceId) implements FacilityEvent {}

    @TypeName("booked")
    record Booked() implements FacilityEvent {}
}
// end::events[]