package com.rez.facility.domain;

import java.time.LocalDate;
import java.util.List;

public record ReservationState(State state, String reservationId, String facilityId, String username,
                               int timeSlot, int currentResourceIndex, List<String> resources, LocalDate date) {

    public ReservationState withState(State state) {
        return new ReservationState(state, this.reservationId, this.facilityId, this.username, this.timeSlot,
                this.currentResourceIndex, this.resources, this.date);
    }

    public ReservationState withIncrementedIndex() {
        return new ReservationState(this.state, this.reservationId, this.facilityId, this.username, this.timeSlot,
                this.currentResourceIndex + 1, this.resources, this.date);
    }

    public enum State {
        INIT, SELECTING, FULFILLED, DONE, UNAVAILABLE
    }
}
