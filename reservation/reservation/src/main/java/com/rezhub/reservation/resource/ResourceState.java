package com.rezhub.reservation.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.*;

/**
 * Models a resource to be booked.
 * A resource has:
 * <ul>
 *     <li>a name (identifying that resource within the facility)</li>
 *     <li>a maximum future bookable time, setting the bookable time period</li>
 *     <li>an association of time -> reservation id</li>
 *     <li>an optional weekly schedule restricting which hours are bookable</li>
 *     <li>a resourceType tag (e.g. "court", "player") for filtering</li>
 *     <li>an optional externalRef pointing to the canonical record in another bounded context</li>
 *     <li>an optional externalGroupRef for group/container reverse-lookup (e.g. facility in external service)</li>
 * </ul>
 */
public record ResourceState(
    String name,
    String calendarId,
    Map<LocalDateTime, String> timeWindow,
    Period period,
    Map<DayOfWeek, Set<LocalTime>> weeklySchedule,
    String resourceType,
    String externalRef,
    String externalGroupRef
) {
    private static final Logger log = LoggerFactory.getLogger(ResourceState.class);

    public static ResourceState initialize(String name, String calendarId) {
        return new ResourceState(name, calendarId, new TreeMap<>(), Period.ofMonths(3),
            new HashMap<>(), "", "", "");
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
        if (!weeklySchedule.isEmpty()) {
            Set<LocalTime> hours = weeklySchedule.get(dateTime.getDayOfWeek());
            if (hours == null || !hours.contains(dateTime.toLocalTime())) return false;
        }
        return isWithinBounds(dateTime) && !timeWindow.containsKey(dateTime);
    }

    public ResourceState cancel(LocalDateTime dateTime, String reservationId) {
        if (timeWindow.containsKey(dateTime) && timeWindow.get(dateTime).equals(reservationId))
            timeWindow.remove(dateTime);
        return this;
    }

    public ResourceState withWeeklySchedule(Map<DayOfWeek, Set<LocalTime>> schedule) {
        return new ResourceState(name, calendarId, timeWindow, period, schedule, resourceType, externalRef, externalGroupRef);
    }

    public ResourceState withResourceType(String type) {
        return new ResourceState(name, calendarId, timeWindow, period, weeklySchedule, type, externalRef, externalGroupRef);
    }

    public ResourceState withExternalRef(String ref, String groupRef) {
        return new ResourceState(name, calendarId, timeWindow, period, weeklySchedule, resourceType, ref, groupRef);
    }

    @Override
    public String toString() {
        return "ResourceState{" +
                "name='" + name + '\'' +
                ", calendarId='" + calendarId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", externalRef='" + externalRef + '\'' +
                ", timeWindow=" + timeWindow +
                '}';
    }

    static LocalDateTime roundToValidTime(LocalDateTime dateTime) {
        return dateTime.minusMinutes(dateTime.getMinute()).minusSeconds(dateTime.getSecond()).minusNanos(dateTime.getNano());
    }
}
