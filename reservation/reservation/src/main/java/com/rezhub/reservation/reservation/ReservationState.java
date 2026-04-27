package com.rezhub.reservation.reservation;

import java.time.LocalDateTime;
import java.util.*;

import static com.rezhub.reservation.reservation.ReservationState.State.INIT;

public record ReservationState(State state, String reservationId, List<String> emails,
                               Set<String> availableResources, Set<String> resourceIds,
                               Set<String> pendingResourceIds,
                               LocalDateTime dateTime, String resourceId, String recipientId) {

    public static ReservationState initiate(String entityId) {
        List<String> empty = new ArrayList<>();
        return new ReservationState(INIT, entityId, empty, new HashSet<>(), new HashSet<>(), new HashSet<>(),
            LocalDateTime.now(), "", "");
    }

    public ReservationState withState(State state) {
        return new ReservationState(state, reservationId, emails, availableResources, resourceIds, pendingResourceIds,
            dateTime, resourceId, recipientId);
    }

    public ReservationState withResourceId(String resourceId) {
        return new ReservationState(state, reservationId, emails, availableResources, resourceIds, pendingResourceIds,
            dateTime, resourceId, recipientId);
    }

    public ReservationState withEmails(List<String> emails) {
        return new ReservationState(state, reservationId, emails, availableResources, resourceIds, pendingResourceIds,
            dateTime, resourceId, recipientId);
    }

    public ReservationState withResourceIds(Set<String> resourceIds) {
        return new ReservationState(state, reservationId, emails, availableResources, new HashSet<>(resourceIds), pendingResourceIds,
            dateTime, resourceId, recipientId);
    }

    public ReservationState withPendingResourceIds(Set<String> pendingResourceIds) {
        return new ReservationState(state, reservationId, emails, availableResources, resourceIds, new HashSet<>(pendingResourceIds),
            dateTime, resourceId, recipientId);
    }

    public ReservationState withDateTime(LocalDateTime dateTime) {
        return new ReservationState(state, reservationId, emails, availableResources, resourceIds, pendingResourceIds,
            dateTime, resourceId, recipientId);
    }

    public ReservationState withRecipientId(String recipientId) {
        return new ReservationState(state, reservationId, emails, availableResources, resourceIds, pendingResourceIds,
            dateTime, resourceId, recipientId);
    }

    public ReservationState withAdded(String resourceId) {
        this.availableResources.add(resourceId);
        return this;
    }

    public ReservationState withRemoved(String resourceId) {
        this.availableResources.remove(resourceId);
        return this;
    }

    public ReservationState withPendingAdded(String resourceId) {
        this.pendingResourceIds.add(resourceId);
        return this;
    }

    public ReservationState withPendingRemoved(String resourceId) {
        this.pendingResourceIds.remove(resourceId);
        return this;
    }

    boolean hasAvailableResources() {
        return this.availableResources.iterator().hasNext();
    }

    boolean hasPendingResources() {
        return this.pendingResourceIds.iterator().hasNext();
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
