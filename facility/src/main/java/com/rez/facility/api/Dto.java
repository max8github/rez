package com.rez.facility.api;

import com.rez.facility.domain.ReservationState;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.rez.facility.domain.ReservationState.State.INIT;

public final class Dto {
    private Dto(){}

    public record Facility(String name, Address address, Set<String> resourceIds) {

        public com.rez.facility.domain.Facility toFacilityState(String entityId) {
            return new com.rez.facility.domain.Facility(entityId, name, address.toAddressState(), resourceIds);
        }
    }

    public record Address(String street, String city) {
        public com.rez.facility.domain.Address toAddressState() { return new com.rez.facility.domain.Address(street, city); }
    }

    public record Resource(String resourceName, int size) {

        public com.rez.facility.domain.Resource toResourceState() {
            String[] a = new String[size];
            Arrays.fill(a, "");
            return new com.rez.facility.domain.Resource(resourceName, a, size, 0);
        }
    }

    public record Reservation(String username, int timeSlot) {
        public ReservationState toReservationState(String reservationId, String facilityId, List<String> resources) {
            return new ReservationState(INIT, reservationId, username, facilityId, timeSlot,
                    0, resources);
        }
    }
}
