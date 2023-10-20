package com.rezhub.reservation.reservation;

import lombok.With;
import java.time.LocalDateTime;
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
        if(iterator.hasNext()) return iterator.next();
        else return "";
    }


    public enum State {
        INIT, COLLECTING, SELECTING, FULFILLED, CANCELLED, UNAVAILABLE
    }
}
