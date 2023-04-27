package com.rez.facility.domain;

import java.util.Set;

public record Facility(String facilityId, String name, Address address, Set<String> resourceIds) {

    public Facility onCreated(FacilityEvent.Created created) {
        var dto = created.facilityDTO();
        return dto.toFacility(created.entityId());
    }

    public Facility onRenamed(FacilityEvent.Renamed renamed) {
        return new Facility(facilityId, renamed.newName(), address, resourceIds);
    }

    public Facility onChangeAddress(FacilityEvent.AddressChanged addressChanged) {
        return new Facility(facilityId, name, addressChanged.addressDTO().toAddress(), resourceIds);
    }

    public Facility onResourceIdAdded(FacilityEvent.ResourceIdAdded event) {
        resourceIds.add(event.resourceEntityId());
        return new Facility(facilityId, name, address, resourceIds);
    }

    public Facility onResourceIdRemoved(FacilityEvent.ResourceIdRemoved event) {
        resourceIds.remove(event.resourceEntityId());
        return new Facility(facilityId, name, address, resourceIds);
    }
}
