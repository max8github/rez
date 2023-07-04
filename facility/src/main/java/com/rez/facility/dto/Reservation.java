package com.rez.facility.dto;

import com.rez.facility.domain.ReservationState;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.rez.facility.domain.ReservationState.State.INIT;

//todo: there should be just LocalDateTime here, timeSlot, *and* the location, as it will be useful when creating the cal event
//todo: maybe this is my value object to pass around throughout, in which case, i must also add: fac id, res id, rez id
public record Reservation(List<String> emails, int timeSlot, LocalDate date) {
    public static Reservation fromReservationState(ReservationState reservationState) {
        return new Reservation(reservationState.emails(), reservationState.timeSlot(), reservationState.date());
    }

    public ReservationState toReservationState(String reservationId, String facilityId, List<String> resources) {
        return new ReservationState(INIT, reservationId, facilityId, emails, timeSlot,
                -1, resources, date);
    }

    public LocalDateTime toLocalDateTime() {
        return date.atTime(timeSlot, 0, 0, 0);
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
