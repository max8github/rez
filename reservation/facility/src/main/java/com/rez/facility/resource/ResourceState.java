package com.rez.facility.resource;

import com.rez.facility.resource.dto.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

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
public record ResourceState(String name, SortedSet<Resource.Entry> timeWindow, Period period) {
    private static final Logger log = LoggerFactory.getLogger(ResourceState.class);
    public ResourceState {
        timeWindow = new TreeSet<>();
        period = Period.ofMonths(3);
    }

    public ResourceState(String name) {
        this(name, new TreeSet<>(), Period.ofMonths(3));
    }

    public static ResourceState initialize(String name) {
        return new ResourceState(name);
    }

    public ResourceState set(LocalDateTime dateTime, String reservationId) {
        LocalDateTime key = roundToValidTime(dateTime);
        if (key.isBefore(LocalDateTime.now().plus(period))) {
            timeWindow.add(new Resource.Entry(roundToValidTime(key).toString(), reservationId));
            return this;
        } else {
            throw new IllegalArgumentException("Cannot reserve time outside of the bookable period." +
                    "Reservation can be taken from today until " + LocalDateTime.now().plus(period));
        }
    }

    public String get(LocalDateTime dateTime) {
        //the following is crazy, but if i cannot use Map, what can i do? I could use an array,
        //but i don't want to keep all that space empty all the time. Besides, this method is only used in tests.
        Resource.Entry ke = new Resource.Entry(dateTime.toString(), "");
        Optional<Resource.Entry> entry = timeWindow.stream().filter(e -> e.equals(ke)).findFirst();
        return entry.orElse(ke).reservationId();
    }

    public boolean fitsInto(LocalDateTime dateTime) {
        LocalDateTime key = roundToValidTime(dateTime);
        return key.isBefore(LocalDateTime.now().plus(period)) && !timeWindow.contains(new Resource.Entry(key.toString(), ""));
    }

    public ResourceState cancel(LocalDateTime dateTime, String reservationId) {
        LocalDateTime key = roundToValidTime(dateTime);
        Resource.Entry entry = new Resource.Entry(key.toString(), reservationId);
        if (!timeWindow.contains(entry)) {
            log.warn("reservation {} was not present or it was already cancelled for time {}", reservationId, dateTime);
        } else {
            timeWindow.remove(entry);
            log.info("Reservation {} was removed from resource {} for time {}", reservationId, name, key);
        }
        return this;
    }

    @Override
    public String toString() {
        return "ResourceState{" +
                "name='" + name + '\'' +
                ", timeWindow=" + timeWindow +
                '}';
    }

    public SortedSet<Resource.Entry> timeWindow() {
        return timeWindow;
    }

    private LocalDateTime roundToValidTime(LocalDateTime dateTime) {
        if (dateTime.getMinute() == 0 && dateTime.getSecond() == 0) return dateTime;
        int minute = dateTime.getMinute();
        LocalDateTime oClock = dateTime.minusMinutes(minute).minusSeconds(dateTime.getSecond()).minusNanos(dateTime.getNano());
        return (minute < 30) ? oClock : oClock.plusHours(1);
    }
}