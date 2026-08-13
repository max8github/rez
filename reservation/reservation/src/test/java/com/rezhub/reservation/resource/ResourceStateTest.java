package com.rezhub.reservation.resource;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceStateTest {

    private static LocalDateTime nextMonday() {
        LocalDateTime d = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) d = d.plusDays(1);
        return d;
    }

    @Test
    void set_locksExactInterval_andCancelReleasesIt() {
        ResourceState r = ResourceState.initialize("court1", null);
        LocalDateTime start = nextMonday().withHour(14).withMinute(0);

        assertTrue(r.isReservableAt(start, 60));
        r = r.set(start, start.plusMinutes(60), "rez-1");
        assertFalse(r.isReservableAt(start, 60));

        r = r.cancel("rez-1");
        assertTrue(r.isReservableAt(start, 60));
    }

    @Test
    void adjacentBookings_backToBack_doNotCollide() {
        ResourceState r = ResourceState.initialize("court1", null);
        LocalDateTime start = nextMonday().withHour(14).withMinute(0);
        r = r.set(start, start.plusMinutes(60), "rez-1");

        // starts exactly when rez-1 ends: no real overlap
        assertTrue(r.isReservableAt(start.plusMinutes(60), 60));
    }

    /**
     * The headline bug this fix addresses: two reservations that round to different top-of-the-hour
     * keys under the old single-key model, but genuinely overlap in real time, must now collide.
     */
    @Test
    void overlappingBookings_acrossDifferentHours_collide() {
        ResourceState r = ResourceState.initialize("court1", null).withBookingGranularityMinutes(30);
        LocalDateTime start = nextMonday().withHour(15).withMinute(30);

        r = r.set(start, start.plusMinutes(60), "rez-1"); // 15:30-16:30

        // 16:00-17:00 truly overlaps 15:30-16:30, even though it starts a different hour
        assertFalse(r.isReservableAt(start.plusMinutes(30), 60));
    }

    @Test
    void multiHourBooking_blocksLaterHourItSpans() {
        // Even at the default 60-minute granularity: the old model only ever locked a single
        // hour-key regardless of duration, so a 2-hour booking never blocked its second hour.
        ResourceState r = ResourceState.initialize("court1", null);
        LocalDateTime start = nextMonday().withHour(15).withMinute(0);

        r = r.set(start, start.plusMinutes(120), "rez-1"); // 15:00-17:00

        assertFalse(r.isReservableAt(start.plusMinutes(60), 60)); // 16:00-17:00 truly overlaps
    }

    @Test
    void bookingGranularity_rejectsMisalignedStart() {
        ResourceState r = ResourceState.initialize("court1", null); // default 60-min granularity
        LocalDateTime misaligned = nextMonday().withHour(14).withMinute(15);

        assertFalse(r.isReservableAt(misaligned, 60));
    }

    @Test
    void bookingGranularity_rejectsMisalignedDuration() {
        ResourceState r = ResourceState.initialize("court1", null); // default 60-min granularity
        LocalDateTime start = nextMonday().withHour(14).withMinute(0);

        assertFalse(r.isReservableAt(start, 45));
    }

    @Test
    void bookingGranularity_allowsFinerGranularityWhenConfigured() {
        ResourceState r = ResourceState.initialize("court1", null).withBookingGranularityMinutes(15);
        LocalDateTime start = nextMonday().withHour(14).withMinute(15);

        assertTrue(r.isReservableAt(start, 45));
    }

    @Test
    void weeklySchedule_blocksHoursOutsideSchedule() {
        ResourceState r = ResourceState.initialize("court1", null);
        // Monday 14:00-16:00 only
        Map<DayOfWeek, Set<LocalTime>> schedule = Map.of(
            DayOfWeek.MONDAY, Set.of(LocalTime.of(14, 0), LocalTime.of(15, 0))
        );
        r = r.withWeeklySchedule(schedule);

        LocalDateTime mon14 = nextMonday().withHour(14).withMinute(0);
        LocalDateTime mon15 = mon14.plusHours(1);
        LocalDateTime mon10 = mon14.minusHours(4);
        LocalDateTime tue14 = mon14.plusDays(1); // Tuesday, not in schedule

        assertTrue(r.isReservableAt(mon14, 60));
        assertTrue(r.isReservableAt(mon15, 60));
        assertFalse(r.isReservableAt(mon10, 60));
        assertFalse(r.isReservableAt(tue14, 60));
    }

    @Test
    void weeklySchedule_empty_allowsAllHours() {
        ResourceState r = ResourceState.initialize("court1", null);
        // No schedule set — existing behaviour: any time within the booking window is reservable
        LocalDateTime soon = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
        assertTrue(r.isReservableAt(soon, 60));
    }

    @Test
    void weeklySchedule_bookedSlotIsUnavailableEvenIfInSchedule() {
        ResourceState r = ResourceState.initialize("court1", null);
        Map<DayOfWeek, Set<LocalTime>> schedule = Map.of(
            DayOfWeek.MONDAY, Set.of(LocalTime.of(14, 0))
        );
        r = r.withWeeklySchedule(schedule);
        LocalDateTime mon14 = nextMonday().withHour(14).withMinute(0);

        assertTrue(r.isReservableAt(mon14, 60));
        r = r.set(mon14, mon14.plusMinutes(60), "rez-99");
        assertFalse(r.isReservableAt(mon14, 60));
    }
}
