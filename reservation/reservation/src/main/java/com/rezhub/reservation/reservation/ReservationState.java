package com.rezhub.reservation.reservation;

import com.rezhub.reservation.dto.SelectionItem;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.rezhub.reservation.reservation.ReservationState.State.INIT;

public record ReservationState(State state, String reservationId, List<String> emails,
                               Set<String> availableResources, Set<SelectionItem> selection,
                               LocalDateTime dateTime, String resourceId, String recipientId) {

    public static ReservationState initiate(String entityId) {
        List<String> empty = new ArrayList<>();
        return new ReservationState(INIT, entityId, empty, new HashSet<>(), new HashSet<>(), LocalDateTime.now(), "", "");
    }

    public Set<String> selectionIds() {
        return selection.stream().map(SelectionItem::id).collect(Collectors.toUnmodifiableSet());
    }

    public ReservationState withState(State state) {
        return new ReservationState(state, reservationId, emails, availableResources, selection, dateTime, resourceId, recipientId);
    }

    public ReservationState withResourceId(String resourceId) {
        return new ReservationState(state, reservationId, emails, availableResources, selection, dateTime, resourceId, recipientId);
    }

    public ReservationState withEmails(List<String> emails) {
        return new ReservationState(state, reservationId, emails, availableResources, selection, dateTime, resourceId, recipientId);
    }

    public ReservationState withSelection(Set<SelectionItem> selection) {
        return new ReservationState(state, reservationId, emails, availableResources, selection, dateTime, resourceId, recipientId);
    }

    public ReservationState withDateTime(LocalDateTime dateTime) {
        return new ReservationState(state, reservationId, emails, availableResources, selection, dateTime, resourceId, recipientId);
    }

    public ReservationState withRecipientId(String recipientId) {
        return new ReservationState(state, reservationId, emails, availableResources, selection, dateTime, resourceId, recipientId);
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
