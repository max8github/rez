package com.rez.facility.resource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
}