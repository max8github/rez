package com.rez.facility.resource;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Models a resource to be booked.
 * A resource has:
 * <ul>
 *     <li>a name (identifying that resource within the facility)</li>
 *     <li>a maximum future bookable time, setting the bookable time period</li>
 *     <li>a Map of time -> reservation id</li>
 * </ul>
 * The operations are:
 * <ul>
 *     <li>inquire if a time is reservable</li>
 *     <li>reserve a time</li>
 *     <li>cancel a reservation</li>
 * </ul>
 * When inquiring, a peek on the key, the iso date-time, needs to be checked on the Map.
 * When reserving, validation is performed first and then the datetime key is put into the Map with the reservation
 * id as the value.
 * <br>
 * The validation when reserving is about time period and key.
 * The key needs to be set correct depending on policy. For example, if a reservation's datetime is
 * 2023-09-28T08:07, and only full hours are bookable, then the key 2023-09-28T08:07 needs to be automatically corrected
 * and transformed to 2023-09-28T08:00.
 * The datetime needs to be also within the timeframe allowed for reservations.
 */
@Accessors(fluent = true)
public class ResourceState {
    @Getter
    private final String name;
    private final SortedMap<LocalDateTime, String> map;
    private final Period period;
    private static final Logger log = LoggerFactory.getLogger(ResourceState.class);

    public ResourceState(String name) {
        this.name = name;
        this.map = new TreeMap<>();
        this.period = Period.ofMonths(3);
    }

    public static ResourceState initialize(String name) {
        return new ResourceState(name);
    }
    public ResourceState set(LocalDateTime dateTime, String reservationId) {
        if (dateTime.isBefore(LocalDateTime.now().plus(period))) {
            map.put(roundToValidTime(dateTime), reservationId);
            return this;
        } else {
            throw new IllegalArgumentException("Cannot reserve time outside of the bookable period." +
                    "Reservation can be taken from today until " + LocalDateTime.now().plus(period));
        }
    }

    public boolean fitsInto(LocalDateTime dateTime) {
        LocalDateTime key = roundToValidTime(dateTime);
        return key.isBefore(LocalDateTime.now().plus(period)) && !map.containsKey(key);
    }

    private LocalDateTime roundToValidTime(LocalDateTime dateTime) {
        if (dateTime.getMinute() == 0 && dateTime.getSecond() == 0) return dateTime;
        int minute = dateTime.getMinute();
        LocalDateTime oClock = dateTime.minusMinutes(minute).minusSeconds(dateTime.getSecond());
        return (minute < 30) ? oClock : oClock.plusHours(1);
    }

    public ResourceState cancel(LocalDateTime dateTime, String reservationId) {
        LocalDateTime key = roundToValidTime(dateTime);
        if (!map.containsKey(key)) {
            log.warn("reservation {} was not present or it was already cancelled for time {}", reservationId, dateTime);
        } else if(!map.get(key).equals(reservationId)) {
            log.error("A cancellation was requested on reservation id {}, but reservation id {} was found for time {}",
                    reservationId, map.get(key), dateTime);
            throw new IllegalStateException("Cancellation of wrong reservation");
        } else {
            String oldRezId = map.get(key);
            log.debug("Reservation {} was removed from resource {}, which had res id '{}'", reservationId, name, oldRezId);
            log.info("Reservation {} was removed from resource {} for time {}", reservationId, name, key);
            map.remove(key);
        }
        return this;
    }

    @Override
    public String toString() {
        return "ResourceState{" +
                "name='" + name + '\'' +
                ", map=" + map +
                '}';
    }

    public Map<LocalDateTime, String> timeWindow() {
        return map;
    }

}