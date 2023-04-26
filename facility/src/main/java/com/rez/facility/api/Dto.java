package com.rez.facility.api;

import com.rez.facility.domain.Address;
import com.rez.facility.domain.Facility;
import com.rez.facility.domain.Resource;

import java.util.ArrayList;
import java.util.List;

public final class Dto {
    private Dto(){}

    public record FacilityDTO(String name, AddressDTO address, List<ResourceDTO> resources) {

            public FacilityDTO withName(String newName) {
                return new FacilityDTO(newName, address, resources);
            }

            public FacilityDTO withAddress(AddressDTO newAddress) {
                return new FacilityDTO(name, newAddress, resources);
            }

            public FacilityDTO withResources(List<ResourceDTO> newRes) {

                return new FacilityDTO(name, address, newRes);
            }

            public Facility toFacility(String entityId) {
                return new Facility(entityId, name, address.toAddress(), new ArrayList<Resource>());
            }
    }

    public record AddressDTO(String street, String city) {
        public Address toAddress() { return new Address(street, city); }
    }

    public record ResourceDTO(String resourceId, int size) {

        public static ResourceDTO of(Resource resource, int timeSlotsSize) {
            return new ResourceDTO(resource.resourceId(), timeSlotsSize);
        }

        public Resource toResource() {
            return new Resource(resourceId, new String[size], size, 0);
        }
    }
}
