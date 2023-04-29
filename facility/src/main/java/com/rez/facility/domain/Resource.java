package com.rez.facility.domain;

/**
 * Models a resource to be booked, like a tennis court, a soccer field, or a conference or a yoga room.
 * A resource has a name (identifying that resource within the facility) and a circular time array of
 * available slots for people to book.
 * @param name name/identifier of the resource, as it is set by the facility
 * @param timeWindow array of time slots, like hours in a day.
 * @param size size of the time window
 * @param nowPointer array index that points to the slot we are in right now.
 */
public record Resource(String name, String[] timeWindow, int size, int nowPointer) {}