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

        public static Facility fromFacilityState(com.rez.facility.domain.Facility facilityState) {
            return new Facility(facilityState.name(), Address.fromAddressState(facilityState.address()), facilityState.resourceIds());
        }
    }

    public record Address(String street, String city) {
        public com.rez.facility.domain.Address toAddressState() { return new com.rez.facility.domain.Address(street, city); }
        public static Address fromAddressState(com.rez.facility.domain.Address addressState) {
            return new Address(addressState.street(), addressState.city());
        }
    }

    public record Resource(String resourceName, int size) {

        public static Resource fromResourceState(com.rez.facility.domain.Resource resourceState) {
            return new Resource(resourceState.name(), resourceState.size());
        }

        public com.rez.facility.domain.Resource toResourceState() {
            return com.rez.facility.domain.Resource.initialize(resourceName, size);
        }
    }

    public record Reservation(String username, int timeSlot) {
        public static Reservation fromReservationState(ReservationState reservationState) {
            return new Reservation(reservationState.username(), reservationState.timeSlot());
        }

        ReservationState toReservationState(String reservationId, String facilityId, List<String> resources) {
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
