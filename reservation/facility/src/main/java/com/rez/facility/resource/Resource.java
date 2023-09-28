package com.rez.facility.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Models a resource to be booked.
 * A resource has a name (identifying that resource within the facility) and a circular time array of
 * available slots for people to book.
 *
 * @param name       name/identifier of the resource, as it is set by the facility. Example: 'Conference Room 25'
 * @param timeWindow array of time slots, like hours in a day.
 * @param size       size of the time window
 * @param nowPointer array index that points to the slot we are in right now.
 */
public record Resource(String name, String[] timeWindow, int size, int nowPointer) {
    private static final Logger log = LoggerFactory.getLogger(Resource.class);
    public static Resource initialize(String name, int size) {
        String[] tw = new String[size];
        Arrays.fill(tw, "");
        return new Resource(name, tw, size, 0);
    }
    public Resource withTimeWindow(int timeSlot, String reservationId) {
        if (timeSlot < timeWindow.length)
            this.timeWindow[timeSlot] = reservationId;
        return this;
    }
    public Resource cancel(LocalDateTime dateTime, String reservationId) {
        int timeSlot = toTimeSlot(dateTime);
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

    //todo: it is not like this in general (could be broken in half hours, quarters, etc). timeSlot is an implementation
    //detail of Resource, it should not surface here. In the end, a Resource is probably just a list of reservations
    //and for that, it should just print those out, when inquired about its state.
    public static int toTimeSlot(LocalDateTime dateTime) {
        return dateTime.getHour();
    }

    @Override
    public String toString() {
        return "Resource{" +
                "name='" + name + '\'' +
                ", timeWindow=" + Arrays.toString(timeWindow) +
                ", size=" + size +
                ", nowPointer=" + nowPointer +
                '}';
    }
}