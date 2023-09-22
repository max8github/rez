package com.rez.facility.dto;

public record Address(String street, String city) {
    public com.rez.facility.domain.Address toAddressState() {
        return new com.rez.facility.domain.Address(street, city);
    }

    public static Address fromAddressState(com.rez.facility.domain.Address addressState) {
        return new Address(addressState.street(), addressState.city());
    }
}
