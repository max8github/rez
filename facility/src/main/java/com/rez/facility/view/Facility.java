package com.rez.facility.view;


import com.rez.facility.domain.Address;

public record Facility(String name, Address address, String id) {

    public Facility withName(String newName) {
        return new Facility(newName, address, id);
    }

    public Facility withAddress(Address newAddress) {
        return new Facility(name, newAddress, id);
    }
}
