package com.rez.facility.resource.dto;

import java.util.Comparator;
import java.util.Objects;

public record Resource(String resourceId, String resourceName, int size) {

    public record Entry(String dateTime, String reservationId) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry that) {
            return Objects.compare(this, that,
                    Comparator.comparing(Entry::dateTime));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return Objects.equals(dateTime, entry.dateTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dateTime);
        }
    }
}
