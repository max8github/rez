package com.rez.facility.api;

import org.junit.jupiter.api.Test;

import java.util.List;

class DelegatingServiceActionTest {

    @Test
    void testListToString() {
        List<String> attendees = List.of("Bob", "Tomas", "John");
        String messageContent = String.join(",", attendees);
        System.out.println("messageContent = " + messageContent);
    }
}