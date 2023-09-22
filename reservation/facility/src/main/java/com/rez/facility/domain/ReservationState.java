package com.rez.facility.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static com.rez.facility.domain.ReservationState.State.INIT;

public record ReservationState(State state, String reservationId, String facilityId, List<String> emails,
                               int timeSlot, int currentResourceIndex, List<String> resources, LocalDate date) {

    public static ReservationState initiate(String entityId) {
        List<String> empty = Collections.emptyList();
        return new ReservationState(INIT, entityId, "", empty, 0, -1, empty, LocalDate.now());

    }

    public ReservationState withState(State state) {
        return new ReservationState(state, this.reservationId, this.facilityId, this.emails, this.timeSlot,
                this.currentResourceIndex, this.resources, this.date);
    }

    public ReservationState withIncrementedIndex() {
        return new ReservationState(this.state, this.reservationId, this.facilityId, this.emails, this.timeSlot,
                this.currentResourceIndex + 1, this.resources, this.date);
    }

    public enum State {
        INIT, SELECTING, FULFILLED, CANCELLED, UNAVAILABLE
    }
}
