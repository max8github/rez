package com.rez.facility.domain;

import java.util.List;

public record Reservation(State state, String reservationId, String username, String facilityId,
                          int timeSlot, int numOfResponses, List<String> resources) {

    public Reservation withState(State state) {
        return new Reservation(state, this.reservationId, this.username, this.facilityId, this.timeSlot,
                this.numOfResponses, this.resources);
    }

    public enum State {
        INIT, SELECTING, FULLFILLED, DONE, UNAVAILABLE
    }
}
