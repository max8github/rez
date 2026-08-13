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
 *
 * <h2>Locking model</h2>
 * {@code timeWindow} is the double-booking guard: a {@code NavigableMap<LocalDateTime, Booking>} keyed
 * by each held reservation's real, unrounded start time, valued with its real end time and the
 * reservationId holding it. There is no slot grid — {@link #isReservableAt(LocalDateTime, int)} checks
 * for overlap against the immediate {@code floorEntry}/{@code ceilingEntry} neighbors of the candidate
 * start, which is exact for any start/duration combination (no false negatives from rounding, no false
 * positives from grid misalignment). A 3:30–4:30 booking and a 4:00–5:00 booking correctly collide.
 * <p>
 * Note this class previously kept one key per reservation rounded down to the top of the hour, with no
 * duration concept at all — two reservations that rounded to different hours never collided even when
 * their real time ranges overlapped. That bug is what this interval-based model replaces.
 *
 * <h2>Booking granularity (validation, separate from locking)</h2>
 * {@code bookingGranularityMinutes} is a per-resource rule for what start times/durations are valid to
 * request at all — e.g. a court bookable only in 60-minute chunks starting on the hour, or a room
 * bookable in 15-minute increments. It has nothing to do with "now": booking the 17:00-18:00 slot at
 * 17:09 is fine (17:00 is a valid grid point; the requester just loses those 9 minutes), but a request
 * for 17:30 on an hourly-granularity resource is rejected outright, regardless of the current time.
 * A misaligned request is rejected, not silently rounded to the nearest valid slot.
 *
 * @param name identifies this resource within its facility (e.g. "Court 1")
 * @param calendarId the Google Calendar ID this resource's bookings are mirrored to, if any
 * @param timeWindow the double-booking lock: one entry per currently-held reservation, keyed by its
 *                    real start time — see "Locking model" above
 * @param period how far into the future this resource can be booked (a rolling window from "now")
 * @param weeklySchedule per-day-of-week set of bookable start times; empty means no restriction
 *                        (any time within {@code period} is bookable, subject to booking granularity)
 * @param bookingGranularityMinutes the smallest booking increment this resource accepts (e.g. 60, 30,
 *                                  15) — see "Booking granularity" above
 * @param resourceType a tag for filtering/categorizing resources (e.g. "court", "player")
 * @param externalRef pointer to this resource's canonical record in another bounded context, if any
 * @param externalGroupRef pointer used for group/container reverse-lookup (e.g. the facility ID in
 *                          the external service that owns this resource), if any
 */
public record ResourceState(
    String name,
    String calendarId,
    NavigableMap<LocalDateTime, ResourceState.Booking> timeWindow,
    Period period,
    Map<DayOfWeek, Set<LocalTime>> weeklySchedule,
    int bookingGranularityMinutes,
    String resourceType,
    String externalRef,
    String externalGroupRef
) {
    private static final Logger log = LoggerFactory.getLogger(ResourceState.class);

    public static final int DEFAULT_BOOKING_GRANULARITY_MINUTES = 60;

    /** One held reservation: its real end time and the reservationId holding it. */
    public record Booking(LocalDateTime endTime, String reservationId) {}

    public static ResourceState initialize(String name, String calendarId) {
        return new ResourceState(name, calendarId, new TreeMap<>(), Period.ofMonths(3),
            new HashMap<>(), DEFAULT_BOOKING_GRANULARITY_MINUTES, "", "", "");
    }

    public ResourceState set(LocalDateTime start, LocalDateTime end, String reservationId) {
        timeWindow.put(start, new Booking(end, reservationId));
        return this;
    }

    public ResourceState cancel(String reservationId) {
        timeWindow.entrySet().removeIf(e -> e.getValue().reservationId().equals(reservationId));
        return this;
    }

    private boolean isWithinBounds(LocalDateTime start) {
        return start.isBefore(LocalDateTime.now().plus(period));
    }

    /** Is the requested start/duration a valid increment for this resource's booking granularity? */
    private boolean isAlignedToGrid(LocalDateTime start, int durationMinutes) {
        return durationMinutes > 0
            && durationMinutes % bookingGranularityMinutes == 0
            && start.getMinute() % bookingGranularityMinutes == 0
            && start.getSecond() == 0
            && start.getNano() == 0;
    }

    private boolean overlapsExisting(LocalDateTime start, LocalDateTime end) {
        Map.Entry<LocalDateTime, Booking> floor = timeWindow.floorEntry(start);
        if (floor != null && floor.getValue().endTime().isAfter(start)) return true;
        Map.Entry<LocalDateTime, Booking> ceiling = timeWindow.ceilingEntry(start);
        return ceiling != null && ceiling.getKey().isBefore(end);
    }

    public boolean isReservableAt(LocalDateTime start, int durationMinutes) {
        if (!isAlignedToGrid(start, durationMinutes)) return false;
        if (!isWithinBounds(start)) return false;
        if (!weeklySchedule.isEmpty()) {
            Set<LocalTime> hours = weeklySchedule.get(start.getDayOfWeek());
            if (hours == null || !hours.contains(start.toLocalTime())) return false;
        }
        return !overlapsExisting(start, start.plusMinutes(durationMinutes));
    }

    public ResourceState withWeeklySchedule(Map<DayOfWeek, Set<LocalTime>> schedule) {
        return new ResourceState(name, calendarId, timeWindow, period, schedule, bookingGranularityMinutes, resourceType, externalRef, externalGroupRef);
    }

    public ResourceState withBookingGranularityMinutes(int minutes) {
        return new ResourceState(name, calendarId, timeWindow, period, weeklySchedule, minutes, resourceType, externalRef, externalGroupRef);
    }

    public ResourceState withResourceType(String type) {
        return new ResourceState(name, calendarId, timeWindow, period, weeklySchedule, bookingGranularityMinutes, type, externalRef, externalGroupRef);
    }

    public ResourceState withExternalRef(String ref, String groupRef) {
        return new ResourceState(name, calendarId, timeWindow, period, weeklySchedule, bookingGranularityMinutes, resourceType, ref, groupRef);
    }

    @Override
    public String toString() {
        return "ResourceState{" +
                "name='" + name + '\'' +
                ", calendarId='" + calendarId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", externalRef='" + externalRef + '\'' +
                ", bookingGranularityMinutes=" + bookingGranularityMinutes +
                ", timeWindow=" + timeWindow +
                '}';
    }
}
