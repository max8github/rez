package com.rez.facility.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;

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
public record ResourceState(String name, Map<LocalDateTime, String> timeWindow, Period period) {
    private static final Logger log = LoggerFactory.getLogger(ResourceState.class);
    public static ResourceState initialize(String name) {
        return new ResourceState(name, new TreeMap<>(), Period.ofMonths(3));
    }

    public ResourceState set(LocalDateTime dateTime, String reservationId) {
        timeWindow.put(dateTime, reservationId);
        return this;
    }

    public String get(LocalDateTime dateTime) {
        return this.timeWindow.get(dateTime);
    }

    private boolean isWithinBounds(LocalDateTime validTime) {
        return validTime.isBefore(LocalDateTime.now().plus(period));
    }

    public boolean isReservableAt(LocalDateTime dateTime) {
        return isWithinBounds(dateTime) && !timeWindow.containsKey(dateTime);
    }

    public ResourceState cancel(LocalDateTime dateTime, String reservationId) {
        if(timeWindow.containsKey(dateTime) && timeWindow.get(dateTime).equals(reservationId)) timeWindow.remove(dateTime);
        return this;
    }

    @Override
    public String toString() {
        return "ResourceState{" +
                "name='" + name + '\'' +
                ", timeWindow=" + timeWindow +
                ", period=" + period +
                '}';
    }

    static LocalDateTime roundToValidTime(LocalDateTime dateTime) {
        return dateTime.minusMinutes(dateTime.getMinute()).minusSeconds(dateTime.getSecond()).minusNanos(dateTime.getNano());
    }
}