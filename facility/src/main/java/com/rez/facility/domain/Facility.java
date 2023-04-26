package com.rez.facility.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record Facility(String facilityId, List<Resource> resources, boolean booked) {

    public Facility onResourceAdded(FacilityEvent.ResourceAdded resourceAdded) {
        var res = resourceAdded.resource();
        var resource = updateResource(res, this);
        List<Resource> resources = removeResourceById(this, res.resourceId());
        resources.add(resource);
        resources.sort(Comparator.comparing(Resource::resourceId));
        return new Facility(facilityId, resources, booked);
    }

    public Facility onResourceRemoved(FacilityEvent.ResourceRemoved resourceRemoved) {
        List<Resource> updatedResources = removeResourceById(this, resourceRemoved.resourceId());
        updatedResources.sort(Comparator.comparing(Resource::resourceId));
        return new Facility(facilityId, updatedResources, booked);
    }

    private static List<Resource> removeResourceById(
            Facility cart, String resourceId) {
        return cart.resources().stream()
                .filter(resource -> !resource.resourceId().equals(resourceId))
                .collect(Collectors.toList());
    }

    private static Resource updateResource(Resource resource, Facility facility) {
        return facility.findResourceById(resource.resourceId())
                .map(li -> li.withSize(li.size() + resource.size()))
                .orElse(resource);
    }

    public Optional<Resource> findResourceById(String resourceId) {
        Predicate<Resource> resourceExists =
                resource -> resource.resourceId().equals(resourceId);
        return resources.stream().filter(resourceExists).findFirst();
    }

    public Facility onBooked() {
        return new Facility(facilityId, resources, true);
    }
}
