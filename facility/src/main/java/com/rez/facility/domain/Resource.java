package com.rez.facility.domain;

import com.rez.facility.api.Dto;

/**
 * Models a resource to be booked, like a tennis court, a soccer field, or a conference or a yoga room.
 * A resource has a name (identifying that resource within the facility) and a circular time array of
 * available slots for people to book.
 * @param name name/identifier of the resource, as it is set by the facility. Example: 'Conference Room 25'
 * @param timeWindow array of time slots, like hours in a day.
 * @param size size of the time window
 * @param nowPointer array index that points to the slot we are in right now.
 */
public record Resource(String name, String[] timeWindow, int size, int nowPointer) {
    public static Resource initialize() {
        return new Resource("", new String[1], 1, 0);
    }
    public boolean hasAvailable(Dto.ReservationDTO dto) {
         return timeWindow[dto.timeSlot()].isEmpty();
    }
    public Resource fill(Dto.ReservationDTO dto) {
        timeWindow[dto.timeSlot()] = dto.username();
        return this;
    }
}