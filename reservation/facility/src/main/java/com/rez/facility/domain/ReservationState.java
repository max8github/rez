package com.rez.facility.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static com.rez.facility.domain.ReservationState.State.INIT;

public record ReservationState(State state, String reservationId, String facilityId, List<String> emails,
                               int currentResourceIndex, List<String> resources, LocalDateTime dateTime) {

    public static ReservationState initiate(String entityId) {
        List<String> empty = Collections.emptyList();
        return new ReservationState(INIT, entityId, "", empty, -1, empty, LocalDateTime.now());

    }

    public ReservationState withState(State state) {
        return new ReservationState(state, this.reservationId, this.facilityId, this.emails,
                this.currentResourceIndex, this.resources, this.dateTime);
    }

    public ReservationState withIncrementedIndex() {
        return new ReservationState(this.state, this.reservationId, this.facilityId, this.emails,
                this.currentResourceIndex + 1, this.resources, this.dateTime);
    }

    public long deadline() {
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public enum State {
        INIT, SELECTING, FULFILLED, CANCELLED, CANCEL_REQUESTED, UNAVAILABLE, COMPLETE
    }
}
