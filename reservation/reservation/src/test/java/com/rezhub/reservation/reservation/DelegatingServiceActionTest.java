package com.rezhub.reservation.reservation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DelegatingServiceActionTest {

    @Test
    void testListToString() {
        List<String> attendees = List.of("Bob", "Tomas", "John");
        String messageContent = String.join(",", attendees);
        assertEquals("Bob,Tomas,John", messageContent);
    }
}