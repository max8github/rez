package com.rez.facility.reservation;

import lombok.With;
import java.time.LocalDateTime;
import java.util.*;

import static com.rez.facility.reservation.ReservationState.State.INIT;

@With
public record ReservationState(State state, String reservationId, String facilityId, List<String> emails,
                               int currentResourceIndex, List<String> resources, LocalDateTime dateTime) {

    public static ReservationState initiate(String entityId) {
        List<String> empty = new ArrayList<>();
        return new ReservationState(INIT, entityId, "", empty, -1, empty, LocalDateTime.now());

    }

    public ReservationState withIncrementedIndex() {
        return new ReservationState(this.state, this.reservationId, this.facilityId, this.emails,
                this.currentResourceIndex + 1, this.resources, this.dateTime);
    }

    public enum State {
        INIT, SELECTING, FULFILLED, CANCELLED, UNAVAILABLE
    }
}
