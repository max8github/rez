package com.rez.facility.api;

import com.rez.facility.domain.ReservationState;

import java.util.List;
import java.util.Set;

import static com.rez.facility.domain.ReservationState.State.INIT;

public final class Mod {
    private Mod(){}

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
            return com.rez.facility.domain.Resource.initialize(resourceName, size);
        }
    }

    public record Reservation(String username, int timeSlot) {
        public ReservationState toReservationState(String reservationId, String facilityId, List<String> resources) {
            return new ReservationState(INIT, reservationId, facilityId, username, timeSlot,
                    -1, resources);
        }

//        public static Reservation initialize(String reservationId, String facilityId,
//                                                  String username, String timeSlot, List<String> resources) {
//            return new ReservationState(INIT, reservationId, facilityId, username, timeSlot,
//                    -1, resources);
//        }

        public boolean fitsInto(com.rez.facility.domain.Resource r) {
            if (timeSlot < r.timeWindow().length)
                return r.timeWindow()[timeSlot].isEmpty();
            else return false;
        }

        public com.rez.facility.domain.Resource setInto(com.rez.facility.domain.Resource r) {
            return r.withTimeWindow(timeSlot, username);
        }
    }
}
