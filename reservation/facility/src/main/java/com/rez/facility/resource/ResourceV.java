package com.rez.facility.resource;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public record ResourceV(String facilityId, String resourceId, String resourceName, SortedSet<Entry> timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.entityId();
        com.rez.facility.resource.dto.Resource resourceDto = created.resourceDto();
        String name = resourceDto == null ? "noname" : resourceDto.resourceName();
        return new ResourceV(facilityId, resourceId, name, new TreeSet<>());
    }

    ResourceV withBooking(LocalDateTime dateTime, String fill) {
        timeWindow.add(new Entry(dateTime.toString(), fill));
        return this;
    }

    ResourceV withoutBooking (LocalDateTime dateTime, String reservationId){
        this.timeWindow.remove(new Entry(dateTime.toString(), reservationId));
        return this;
    }
}


record Entry(String dateTime, String reservationId) implements Comparable<Entry> {
    @Override
    public int compareTo(Entry that) {
        return Objects.compare(this, that,
                Comparator.comparing(Entry::dateTime));
    }
}