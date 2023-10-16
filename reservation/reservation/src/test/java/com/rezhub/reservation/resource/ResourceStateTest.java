package com.rezhub.reservation.resource;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourceStateTest {

    @Test
    void testPrint() {
        ResourceState r = ResourceState.initialize("hotel1");
        LocalDateTime now = LocalDateTime.now();
        r.set(now, "1000");
        r.set(now.plusHours(1), "2000");
        System.out.println("r = " + r);
        System.out.println("r.timeWindow() = " + r.timeWindow());
    }

    @Test
    void testSet() {
        ResourceState r = ResourceState.initialize("hotel1");
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
        ResourceState r = ResourceState.initialize("hotel1");
        LocalDateTime now = LocalDateTime.now();
        String reservationId = "1000";
        r.set(now, reservationId);
        assertFalse(r.isReservableAt(now));
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