package com.rez.facility.domain;

import java.util.List;

public record ReservationState(State state, String reservationId, String facilityId, String username,
                               int timeSlot, int currentResourceIndex, List<String> resources) {

    public ReservationState withState(State state) {
        return new ReservationState(state, this.reservationId, this.facilityId, this.username, this.timeSlot,
                this.currentResourceIndex, this.resources);
    }
//
//    public ReservationState withNumOfResponses(int n) {
//        return new ReservationState(this.state, this.reservationId, this.facilityId, this.username, this.timeSlot,
//                n, this.resources);
//    }

    public enum State {
        INIT, SELECTING, FULLFILLED, DONE, UNAVAILABLE
    }
}
