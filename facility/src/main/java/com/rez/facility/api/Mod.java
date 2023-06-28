package com.rez.facility.api;

import com.rez.facility.domain.ReservationState;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.rez.facility.domain.ReservationState.State.INIT;

public final class Mod {
    private Mod(){}

    public record Facility(String name, Address address, Set<String> resourceIds) {

        public com.rez.facility.domain.Facility toFacilityState(String entityId) {
            return com.rez.facility.domain.Facility.create(entityId)
                    .withName(name)
                    .withAddress(address.toAddressState())
                    .withResourceIds(resourceIds);
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

    public record Resource(String resourceId, String resourceName, int size) {

        public static Resource fromResourceState(com.rez.facility.domain.Resource resourceState, String resourceId) {
            return new Resource(resourceId, resourceState.name(), resourceState.size());
        }

        public com.rez.facility.domain.Resource toResourceState() {
            return com.rez.facility.domain.Resource.initialize(resourceName, size);
        }
    }

    //todo: there should be just LocalDateTime here, timeSlot, *and* the location, as it will be useful when creating the cal event
    //todo: maybe this is my value object to pass around throughout, in which case, i must also add: fac id, res id, rez id
    public record Reservation(List<String> emails, int timeSlot, LocalDate date) {
        public static Reservation fromReservationState(ReservationState reservationState) {
            return new Reservation(reservationState.emails(), reservationState.timeSlot(), reservationState.date());
        }

        ReservationState toReservationState(String reservationId, String facilityId, List<String> resources) {
            return new ReservationState(INIT, reservationId, facilityId, emails, timeSlot,
                    -1, resources, date);
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

        public com.rez.facility.domain.Resource setInto(com.rez.facility.domain.Resource r, String reservationId) {
            return r.withTimeWindow(timeSlot, reservationId);
        }
    }
}
