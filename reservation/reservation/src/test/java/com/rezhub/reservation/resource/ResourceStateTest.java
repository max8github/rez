package com.rezhub.reservation.resource;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourceStateTest {

    @Test
    void testPrint() {
        ResourceState r = ResourceState.initialize("hotel1", null);
        LocalDateTime now = LocalDateTime.now();
        r.set(now, "1000");
        r.set(now.plusHours(1), "2000");
        System.out.println("r = " + r);
        System.out.println("r.timeWindow() = " + r.timeWindow());
    }

    @Test
    void testSet() {
        ResourceState r = ResourceState.initialize("hotel1", null);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nowPlus1 = now.plusHours(1);

        String reservationId = "1000";
        assertTrue(r.isReservableAt(now));
        r.set(now, reservationId);
        assertFalse(r.isReservableAt(now));
        assertTrue(r.isReservableAt(nowPlus1));
        r.set(nowPlus1, reservationId);
        assertFalse(r.isReservableAt(nowPlus1));
    }

    @Test
    void testFits() {
        ResourceState r = ResourceState.initialize("hotel1", null);
        LocalDateTime now = LocalDateTime.now();
        String reservationId = "1000";
        r.set(now, reservationId);
        assertFalse(r.isReservableAt(now));
    }

    @Test
    void weeklySchedule_blocksHoursOutsideSchedule() {
        ResourceState r = ResourceState.initialize("court1", null);
        // Monday 14:00–16:00 only
        Map<DayOfWeek, Set<LocalTime>> schedule = Map.of(
            DayOfWeek.MONDAY, Set.of(LocalTime.of(14, 0), LocalTime.of(15, 0))
        );
        r = r.withWeeklySchedule(schedule);

        LocalDateTime mon14 = nextMonday().withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime mon15 = mon14.plusHours(1);
        LocalDateTime mon10 = mon14.minusHours(4);
        LocalDateTime tue14 = mon14.plusDays(1); // Tuesday, not in schedule

        assertTrue(r.isReservableAt(mon14));
        assertTrue(r.isReservableAt(mon15));
        assertFalse(r.isReservableAt(mon10));
        assertFalse(r.isReservableAt(tue14));
    }

    @Test
    void weeklySchedule_empty_allowsAllHours() {
        ResourceState r = ResourceState.initialize("court1", null);
        // No schedule set — existing behaviour: any time within the booking window is reservable
        LocalDateTime soon = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
        assertTrue(r.isReservableAt(soon));
    }

    @Test
    void weeklySchedule_bookedSlotIsUnavailableEvenIfInSchedule() {
        ResourceState r = ResourceState.initialize("court1", null);
        Map<DayOfWeek, Set<LocalTime>> schedule = Map.of(
            DayOfWeek.MONDAY, Set.of(LocalTime.of(14, 0))
        );
        r = r.withWeeklySchedule(schedule);
        LocalDateTime mon14 = nextMonday().withHour(14).withMinute(0).withSecond(0).withNano(0);

        assertTrue(r.isReservableAt(mon14));
        r.set(mon14, "rez-99");
        assertFalse(r.isReservableAt(mon14));
    }

    private LocalDateTime nextMonday() {
        LocalDateTime d = LocalDateTime.now().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) d = d.plusDays(1);
        return d;
    }

    @Test
    void testViewConversions() {
        List<Map.Entry<LocalDateTime, String>> lt = new ArrayList<>();
        lt.add(Map.entry(LocalDateTime.now(), "123"));
        lt.add(Map.entry(LocalDateTime.now(), "300"));
        lt.add(Map.entry(LocalDateTime.now(), "400"));

        TreeMap<LocalDateTime, String> map = lt.stream().collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x + ", " + y,
                                        TreeMap::new));
        System.out.println("map = " + map);

        //reverse
        List<Map.Entry<LocalDateTime, String>> list = map.entrySet().stream().toList();
        System.out.println("list = " + list);
    }
}