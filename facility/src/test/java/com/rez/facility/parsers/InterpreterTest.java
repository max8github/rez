package com.rez.facility.parsers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void parseContent() {
        String isoDate = "2024-03-13T21:34:05.000Z";
        LocalDate date = LocalDateTime.parse(isoDate, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
        String dateString = "2024-03-13";
        assertEquals(dateString, isoDate.substring(0, 10));
        assertEquals(dateString, date.toString());

        String dateF = date.format(DateTimeFormatter.ISO_DATE);
        assertEquals(dateString, dateF);
    }
}