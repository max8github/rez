package com.rez.facility.resource;

import java.time.LocalDateTime;
import java.util.List;

public record ResourceV(String facilityId, String resourceId, String resourceName, List<String[]> timeWindow) {
    public static ResourceV initialize(ResourceEvent.ResourceCreated created) {
        String facilityId = created.facilityId();
        String resourceId = created.entityId();
        com.rez.facility.resource.dto.Resource resourceDto = created.resourceDto();
        ResourceState resourceState = resourceDto == null
                ? ResourceState.initialize("noname")
                : ResourceState.initialize(resourceDto.resourceName());
        return new ResourceV(facilityId, resourceId, resourceState.name(), resourceState.timeWindow());
    }

//    private static Map<LocalDateTime, String> fromListToMap(List<Map.Entry<LocalDateTime, String>> list) {
//        return list.stream().collect(
//                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x + ", " + y,
//                        TreeMap::new));
//    }

    ResourceV withBooking(LocalDateTime dateTime, String fill) {
        timeWindow.add(new String[]{dateTime.toString(), fill});
        return this;
    }

    ResourceV withoutBooking (LocalDateTime dateTime, String reservationId){
        this.timeWindow.remove(new String[]{dateTime.toString(), reservationId});
        return this;
    }
}