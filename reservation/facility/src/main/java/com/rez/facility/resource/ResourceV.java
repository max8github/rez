package com.rez.facility.resource;

import java.time.LocalDateTime;
import java.util.SortedSet;
import java.util.TreeSet;

public record ResourceV(String facilityId, String resourceId, String resourceName, SortedSet<ResourceState.Entry> timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.entityId();
        com.rez.facility.resource.dto.Resource resourceDto = created.resourceDto();
        String name = resourceDto == null ? "noname" : resourceDto.resourceName();
        return new ResourceV(facilityId, resourceId, name, new TreeSet<>());
    }

//    private static Map<LocalDateTime, String> fromListToMap(List<Map.Entry<LocalDateTime, String>> list) {
//        return list.stream().collect(
//                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x + ", " + y,
//                        TreeMap::new));
//    }

    ResourceV withBooking(LocalDateTime dateTime, String fill) {
        timeWindow.add(new ResourceState.Entry(dateTime.toString(), fill));
        return this;
    }

    ResourceV withoutBooking (LocalDateTime dateTime, String reservationId){
        this.timeWindow.remove(new ResourceState.Entry(dateTime.toString(), reservationId));
        return this;
    }
}