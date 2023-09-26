package com.rez.facility.dto;

import com.rez.facility.domain.ReservationState;

import java.time.LocalDateTime;
import java.util.List;

import static com.rez.facility.domain.ReservationState.State.INIT;

//todo: maybe this is my dto to pass around throughout, in which case, i must also add: fac id, res id, rez id
public record Reservation(List<String> emails, LocalDateTime dateTime) {
    public static Reservation fromReservationState(ReservationState reservationState) {
        return new Reservation(reservationState.emails(), reservationState.dateTime());
    }

    public ReservationState toReservationState(String reservationId, String facilityId, List<String> resources) {
        return new ReservationState(INIT, reservationId, facilityId, emails,
                -1, resources, dateTime);
    }

    //todo: it is not like this in general (could be broken in half hours, quarters, etc)
    public int toTimeSlot() {
        return dateTime.toLocalTime().getHour();
    }

    public LocalDateTime toLocalDateTime() {
        return dateTime;
    }

//        public static Reservation initialize(String reservationId, String facilityId,
//                                                  String username, List<String> resources) {
//            return new ReservationState(INIT, reservationId, facilityId, username,
//                    -1, resources);
//        }

    public boolean fitsInto(com.rez.facility.domain.Resource r) {
        if (toTimeSlot() < r.timeWindow().length)
            return r.timeWindow()[toTimeSlot()].isEmpty();
        else return false;
    }

    public com.rez.facility.domain.Resource setInto(com.rez.facility.domain.Resource r, String reservationId) {
        return r.withTimeWindow(toTimeSlot(), reservationId);
    }
}
