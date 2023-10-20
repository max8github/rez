package com.rezhub.reservation.stringparser;

import com.rezhub.reservation.spi.Nlp;
import com.rezhub.reservation.spi.Parser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextMessageParserTest {

    @Test
    void testParseCancel() {
        Nlp nlp = new StringNlpParser();
        Parser parser = new TextMessageParser(nlp);
        String facilityId = "f123";
        String content = "Cancel 1234a325bf34 ok?\n".trim().toLowerCase();
        Parser.TextMessage textMessage = new Parser.TextMessage("chis123", "thid123", content, "John",
                "John", "u123", "2023-09-20T20:22", new Parser.SystemMessage(123, "https://example.com"), "https://example.com");
        Parser.ReservationDto reservationDto = parser.parse(facilityId, textMessage);
        assertEquals("cancel", reservationDto.command());
        assertEquals("1234a325bf34", reservationDto.reservationId());
    }

    @Test
    void testParseCreate() {
        Nlp nlp = new StringNlpParser();
        Parser parser = new TextMessageParser(nlp);
        String facilityId = "f123";
        String content = "2023-08-02, 8, john.doe@example.com";
        Parser.TextMessage textMessage = new Parser.TextMessage("chis123", "thid123", content, "John",
                "tom.smith@example.com", "u123", "2023-09-20T20:22", new Parser.SystemMessage(123, "https://example.com"), "https://example.com");
        Parser.ReservationDto reservationDto = parser.parse(facilityId, textMessage);
        assertEquals("create", reservationDto.command());
        assertEquals(facilityId, reservationDto.facilityId());
        assertEquals(List.of("john.doe@example.com", "tom.smith@example.com"), reservationDto.emails());
    }

    @Test
    void parseIsoDate() {
        String isoDate = "2024-03-13T21:34:05.000Z";
        LocalDate date = LocalDateTime.parse(isoDate, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
        String dateString = "2024-03-13";
        assertEquals(dateString, isoDate.substring(0, 10));
        assertEquals(dateString, date.toString());

        String dateF = date.format(DateTimeFormatter.ISO_DATE);
        assertEquals(dateString, dateF);
    }
}