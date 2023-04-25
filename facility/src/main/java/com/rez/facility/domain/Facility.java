package com.rez.facility.domain;

public record Facility(String name, Address address, Resource resource) {

    public Facility withName(String newName) {
        return new Facility(newName, address, resource);
    }

    public Facility withAddress(Address newAddress) {
        return new Facility(name, newAddress, resource);
    }

    public Facility withResource(Resource newResource) { return new Facility(name, address, newResource); }
}