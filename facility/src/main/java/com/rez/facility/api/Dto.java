package com.rez.facility.api;

import com.rez.facility.domain.Address;
import com.rez.facility.domain.Facility;
import com.rez.facility.domain.Resource;

import java.util.List;
import java.util.stream.Collectors;

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
            return new Facility(entityId, name, address.toAddress(),
                    resources.stream().map(i -> i.toResource()).collect(Collectors.toList()));
        }
    }

    public record AddressDTO(String street, String city) {
        public Address toAddress() { return new Address(street, city); }
        public static AddressDTO of(Address address) {
            return new AddressDTO(address.street(), address.city());
        }
    }

    public record ResourceDTO(String resourceId, int size) {
        public static ResourceDTO of(Resource resource) {
            return new ResourceDTO(resource.resourceId(), resource.size());
        }

        public Resource toResource() {
            return new Resource(resourceId, new String[size], size, 0);
        }
    }
}
