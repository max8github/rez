package com.rezhub.reservation.customer.user;

import com.rezhub.reservation.customer.dto.Address;

public record User(String email, String name, Address address) {

  public User withName(String newName) {
    return new User(email, newName, address);
  }

  public User withAddress(Address newAddress) {
    return new User(email, name, newAddress);
  }
}