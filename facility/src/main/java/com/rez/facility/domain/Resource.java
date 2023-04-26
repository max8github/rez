package com.rez.facility.domain;

/**
 *
 * @param resourceId name/identifier of the resource, as it is set by the facility
 * @param timeWindow array of time slots, like hours in a day.
 * @param size size of the time window
 * @param nowPointer array index that points to the slot we are in right now.
 */
public record Resource(String resourceId, String[] timeWindow, int size, int nowPointer) {
    public Resource withSize(int size) {
        return new Resource(resourceId, timeWindow, size, nowPointer);
    }
}