package com.rez.facility.resource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceStateTest {

    @Test
    void testPrint() {
        ResourceState r = ResourceState.initialize("hotel1");
        LocalDateTime now = LocalDateTime.now();
        r.set(now, "1000");
        r.set(now.plusHours(1), "2000");
        System.out.println("r = " + r);
        System.out.println("r.timeWindow() = " + r.printMap());
    }

    @Test
    void testSet() {
        ResourceState r = ResourceState.initialize("hotel1");
        LocalDateTime now = LocalDateTime.now();
        assertTrue(r.fitsInto(now));
        r.set(now, "1000");
        r.set(now, "1000");
        Assertions.assertFalse(r.fitsInto(now));
        Assertions.assertEquals(1, r.timeWindow().size());
        r.set(now.plusHours(1), "1000");
        Assertions.assertEquals(2, r.timeWindow().size());
    }

    @Test
    void testFits() {
        ResourceState r = ResourceState.initialize("hotel1");
        LocalDateTime now = LocalDateTime.now();
        r.set(now, "1000");
        Assertions.assertFalse(r.fitsInto(now));
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