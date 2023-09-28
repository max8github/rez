package com.rez.facility.pool.dto;

public record Address(String street, String city) {
    public com.rez.facility.pool.Address toAddressState() {
        return new com.rez.facility.pool.Address(street, city);
    }

    public static Address fromAddressState(com.rez.facility.pool.Address addressState) {
        return new Address(addressState.street(), addressState.city());
    }
}
