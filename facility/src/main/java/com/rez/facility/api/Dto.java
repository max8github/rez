package com.rez.facility.api;

import com.rez.facility.domain.Address;
import com.rez.facility.domain.Facility;
import com.rez.facility.domain.ReservationState;
import com.rez.facility.domain.Resource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.rez.facility.domain.ReservationState.State.INIT;

public final class Dto {
    private Dto(){}

    public record FacilityDTO(String name, AddressDTO address, Set<String> resourceIds) {

        public Facility toFacility(String entityId) {
            return new Facility(entityId, name, address.toAddress(), resourceIds);
        }
    }

    public record AddressDTO(String street, String city) {
        public Address toAddress() { return new Address(street, city); }
    }

    public record ResourceDTO(String resourceName, int size) {

        public Resource toResource() {
            String[] a = new String[size];
            Arrays.fill(a, "");
            return new Resource(resourceName, a, size, 0);
        }
    }

    public record Reservation(String username, int timeSlot) {
        public ReservationState toReservationState(String reservationId, String facilityId, List<String> resources) {
            return new ReservationState(INIT, reservationId, username, facilityId, timeSlot,
                    0, resources);
        }
    }
}
