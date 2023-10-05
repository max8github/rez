package com.rez.facility.resource;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

/**
 * Models a resource to be booked.
 * A resource has:
 * <ul>
 *     <li>a name (identifying that resource within the facility)</li>
 *     <li>a maximum future bookable time, setting the bookable time period</li>
 *     <li>an association of time -> reservation id</li>
 * </ul>
 * The operations are:
 * <ul>
 *     <li>inquire if a time is reservable</li>
 *     <li>reserve a time</li>
 *     <li>cancel a reservation</li>
 * </ul>
 * When inquiring, a peek on the key, the iso date-time, needs to be checked.
 * When reserving, validation is performed first and then the datetime/reservation entry is inserted.
 * <br>
 * The validation when reserving is about time period and key.
 * The datetime needs to be set correct depending on policy. For example, if a reservation's datetime is
 * 2023-09-28T08:07, and only full hours are bookable, then the key 2023-09-28T08:07 needs to be automatically corrected
 * and transformed to 2023-09-28T08:00.
 * The datetime needs to be also within the timeframe allowed for reservations.
 */
@AllArgsConstructor
@Accessors(fluent = true)
public class ResourceState {
    private static final Logger log = LoggerFactory.getLogger(ResourceState.class);

    @Getter
    String name = "Nameless";
    String[] timeWindow = new String[]{"","","","","","","","","","","","","","","","","","","","","","","",""};
    int nowPointer = 0;

    public ResourceState() {
    }

    public ResourceState(String name) {
        this.name = name;
    }

    public static ResourceState initialize(String name) {
        return new ResourceState(name);
    }

    public ResourceState set(LocalDateTime dateTime, String reservationId) {
        int timeSlot = dateTime.getHour();
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = reservationId;
        return this;
    }

    public String get(LocalDateTime dateTime) {
        int timeSlot = dateTime.getHour();
        Objects.checkIndex(timeSlot, timeWindow.length);
        return this.timeWindow[timeSlot];
    }

    public boolean isReservableAt(LocalDateTime dateTime) {
        int timeSlot = dateTime.getHour();
        Objects.checkIndex(timeSlot, timeWindow.length);
        return (timeWindow[timeSlot] == null || timeWindow[timeSlot].isEmpty());
    }

    public ResourceState cancel(LocalDateTime dateTime, String reservationId) {
        int timeSlot = dateTime.getHour();
        if (timeWindow[timeSlot] == null || timeWindow[timeSlot].isEmpty()) {
            log.warn("reservation {} was not present or it was already cancelled in time slot {}", reservationId, timeSlot);
        } else if(!timeWindow[timeSlot].equals(reservationId)) {
            log.error("A cancellation was requested on reservation id {}, but reservation id {} was found on time slot {}",
                    reservationId, timeWindow[timeSlot], timeSlot);
            throw new IllegalStateException("Cancellation of wrong reservation");
        } else {
            String oldRezId = timeWindow[timeSlot];
            log.info("Reservation {} was removed from resource {}, which had res id '{}'", reservationId, name, oldRezId);
            timeWindow[timeSlot] = "";
        }
        return this;
    }

    @Override
    public String toString() {
        return "ResourceState{" +
                "name='" + name + '\'' +
                ", timeWindow=" + Arrays.toString(timeWindow) +
                ", nowPointer=" + nowPointer +
                '}';
    }

    public String[] timeWindow() {
        return timeWindow;
    }

    public int nowPointer() {
        return this.nowPointer;
    }

    static LocalDateTime roundToValidTime(LocalDateTime dateTime) {
        return dateTime.minusMinutes(dateTime.getMinute()).minusSeconds(dateTime.getSecond()).minusNanos(dateTime.getNano());
    }
}