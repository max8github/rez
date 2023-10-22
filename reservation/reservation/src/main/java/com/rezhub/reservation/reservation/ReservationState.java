package com.rezhub.reservation.reservation;

import lombok.With;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.rezhub.reservation.reservation.ReservationState.State.INIT;

@With
public record ReservationState(State state, String reservationId, String facilityId, List<String> emails,
                               Set<String> availableResources, Set<String> resources,
                               LocalDateTime dateTime, String resourceId) {

    public static ReservationState initiate(String entityId) {
        List<String> empty = new ArrayList<>();
        return new ReservationState(INIT, entityId, "", empty, new HashSet<>(), new HashSet<>(), LocalDateTime.now(), "");

    }

    public ReservationState withAdded(String resourceId) {
        this.availableResources.add(resourceId);
        return this;
    }

    public ReservationState withRemoved(String resourceId) {
        this.availableResources.remove(resourceId);
        return this;
    }

    boolean hasAvailableResources() {
        return this.availableResources.iterator().hasNext();
    }

    public String pop() {
        Iterator<String> iterator = this.availableResources.iterator();
        if (iterator.hasNext()) return iterator.next();
        else return "";
    }

    public long deadline() {
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }


    /**
     * INIT is the initial empty state.
     * <br>
     * COLLECTING is the state the reservation is in when waiting to gather all resource availability responses.
     * <br>
     * SELECTING is when the reservation entity has identified an available resource and so it proceeds to book it.
     * <br>
     * FULFILLED is when a reservation is committed. It is not COMPLETE, because a FULFILLED reservation could still
     * be cancelled (if it is booked in the future).
     * <br>
     * CANCEL_REQUESTED is when a cancellation is requested and is being carried over.
     * <br>
     * CANCELLED is an end state, like COMPLETE: the reservation is cancelled and there is nothing more to do with it.
     * <br>
     * UNAVAILABLE: no available resources were found in time. This is also an end state. If it takes too long to
     * find an available resource, then the reservation gets expired. An available resource may show up after the
     * reservation has expired, but it will just be too late.
     * <br>
     * COMPLETE is when a reservation becomes history. It is an end state. A timer will set the reservation as complete
     * when the current time is passed the reservation datetime.
     */
    public enum State {
        INIT, COLLECTING, SELECTING, FULFILLED, CANCEL_REQUESTED, CANCELLED, UNAVAILABLE, COMPLETE
    }
}
