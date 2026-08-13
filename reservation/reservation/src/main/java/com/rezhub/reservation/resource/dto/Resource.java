package com.rezhub.reservation.resource.dto;

import java.util.Comparator;
import java.util.Objects;

public record Resource(String resourceId, String resourceName, String calendarId) {
    public static final String FORBIDDEN_NAME = "noname";

    /** One held reservation on a resource's calendar: its real start/end time and which reservation holds it. */
    public record Entry(String startTime, String endTime, String reservationId) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry that) {
            return Objects.compare(this, that,
                    Comparator.comparing(Entry::startTime));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return Objects.equals(reservationId, entry.reservationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reservationId);
        }
    }
}
