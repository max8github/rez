package com.rez.facility.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record Facility(String facilityId, String name, Address address, List<Resource> resources) {

    public Facility onCreated(FacilityEvent.Created created) {
        var dto = created.facilityDTO();
        return dto.toFacility(created.entityId());
    }

    public Facility onResourceAdded(FacilityEvent.ResourceAdded resourceAdded) {
        var resourceDTO = resourceAdded.resource();
        if(findResourceById(resourceDTO.resourceId()).isEmpty()) {
            resources.add(resourceDTO.toResource());
            resources.sort(Comparator.comparing(Resource::resourceId));
        }
        return new Facility(facilityId, name, address, resources);
    }

    public Facility onResourceRemoved(FacilityEvent.ResourceRemoved resourceRemoved) {
        List<Resource> updatedResources = removeResourceById(this, resourceRemoved.resourceId());
        updatedResources.sort(Comparator.comparing(Resource::resourceId));
        return new Facility(facilityId, name, address, updatedResources);
    }

    private static List<Resource> removeResourceById(Facility facility, String resourceId) {
        return facility.resources().stream()
                .filter(resource -> !resource.resourceId().equals(resourceId))
                .collect(Collectors.toList());
    }

    public Optional<Resource> findResourceById(String resourceId) {
        Predicate<Resource> resourceExists =
                resource -> resource.resourceId().equals(resourceId);
        return resources.stream().filter(resourceExists).findFirst();
    }
}
