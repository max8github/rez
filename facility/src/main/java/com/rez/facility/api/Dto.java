package com.rez.facility.api;

import com.rez.facility.domain.Address;
import com.rez.facility.domain.Facility;
import com.rez.facility.domain.Resource;

import java.util.Set;

public final class Dto {
    private Dto(){}

    public record FacilityDTO(String name, AddressDTO address, Set<String> resources) {

        public Facility toFacility(String entityId) {
            return new Facility(entityId, name, address.toAddress(), resources);
        }
    }

    public record AddressDTO(String street, String city) {
        public Address toAddress() { return new Address(street, city); }
    }

    public record ResourceDTO(String resourceName, int size) {

        public Resource toResource() {
            return new Resource(resourceName, new String[size], size, 0);
        }
    }
}
