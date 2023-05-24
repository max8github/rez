package com.rez.facility.domain;

import com.rez.facility.api.Dto;

import java.util.Arrays;

/**
 * Models a resource to be booked, like a tennis court, a soccer field, or a conference or a yoga room.
 * A resource has a name (identifying that resource within the facility) and a circular time array of
 * available slots for people to book.
 *
 * @param name       name/identifier of the resource, as it is set by the facility. Example: 'Conference Room 25'
 * @param timeWindow array of time slots, like hours in a day.
 * @param size       size of the time window
 * @param nowPointer array index that points to the slot we are in right now.
 */
public record Resource(String name, String[] timeWindow, int size, int nowPointer) {
    public static Resource initialize() {
        String[] a = new String[1];
        Arrays.fill(a, "");
        return new Resource("", a, 1, 0);
    }

    public boolean hasAvailable(Dto.ReservationDTO dto) {
        if (dto.timeSlot() < timeWindow.length)
            return timeWindow[dto.timeSlot()].isEmpty();
        else return false;
    }

    public Resource fill(Dto.ReservationDTO dto) {
        if (dto.timeSlot() < timeWindow.length)
            timeWindow[dto.timeSlot()] = dto.username();
        return this;
    }
}